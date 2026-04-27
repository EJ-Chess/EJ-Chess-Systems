import { useState, useCallback, useEffect, useRef } from 'react'
import { Settings2, Copy, Check, ChevronRight } from 'lucide-react'
import { Toaster, toast } from 'sonner'
import { ChessBoard } from './components/ChessBoard'
import { GameInfo } from './components/GameInfo'
import { MoveHistory } from './components/MoveHistory'
import { GameControls } from './components/GameControls'
import { ImportExportModal } from './components/ImportExportModal'
import { HomeConfirmDialog } from './components/HomeConfirmDialog'
import { ChessClock } from './components/ChessClock'
import { LogModal } from './components/LogModal'
import { ClockSettingsModal } from './components/ClockSettingsModal'
import { GameSetupModal } from './components/GameSetupModal'
import {
  chessApi,
  ApiError,
  type GameStateResponse,
  type MoveNotation,
  type CreateGameRequest,
} from './api/chessApi'

const INITIAL_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
const STORAGE_KEY = 'eja-chess-game-id'
const CLOCK_START = 10 * 60 // 10 minutes in seconds

export default function App() {
  const [gameId, setGameId] = useState<string | null>(
    () => localStorage.getItem(STORAGE_KEY),
  )
  const [gameState, setGameState] = useState<GameStateResponse | null>(null)
  const [position, setPosition] = useState<string>(INITIAL_FEN)
  const [legalMoves, setLegalMoves] = useState<MoveNotation[]>([])
  const [pgn, setPgn] = useState<string>('')
  const [loading, setLoading] = useState(false)
  const [showHomeDialog, setShowHomeDialog] = useState(false)
  const [showLog, setShowLog] = useState(false)
  const [showClockSettings, setShowClockSettings] = useState(false)
  const [showGameSetup, setShowGameSetup] = useState(false)

  // ── Game mode ─────────────────────────────────────────────────────────────
  const [playerColor, setPlayerColor] = useState<'white' | 'black'>('white')
  const [opponentType, setOpponentType] = useState<'human' | 'bot'>('human')

  // ── Chess clock ───────────────────────────────────────────────────────────
  const [clockSetting, setClockSetting] = useState(CLOCK_START)
  const [whiteTime, setWhiteTime] = useState(CLOCK_START)
  const [blackTime, setBlackTime] = useState(CLOCK_START)
  const clockIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const isGameOver = !!gameState?.inCheckmate || !!gameState?.inStalemate
  const activePlayer = gameState?.currentTurn ?? null
  const clockRunning = !!gameId && !!gameState && !isGameOver

  // Bot's turn: opponent is bot and current turn is NOT the player's color
  const isBotTurn =
    opponentType === 'bot' &&
    gameState !== null &&
    gameState.currentTurn !== (playerColor.toUpperCase() as 'WHITE' | 'BLACK')

  useEffect(() => {
    if (clockIntervalRef.current) clearInterval(clockIntervalRef.current)
    if (!clockRunning) return

    clockIntervalRef.current = setInterval(() => {
      if (activePlayer === 'WHITE') {
        setWhiteTime((t) => Math.max(t - 1, 0))
      } else {
        setBlackTime((t) => Math.max(t - 1, 0))
      }
    }, 1000)

    return () => {
      if (clockIntervalRef.current) clearInterval(clockIntervalRef.current)
    }
  }, [clockRunning, activePlayer])

  // ── Game-over toasts ──────────────────────────────────────────────────────
  const prevCheckmateRef = useRef(false)
  const prevStalemateRef = useRef(false)

  useEffect(() => {
    const nowCheckmate = !!gameState?.inCheckmate
    const nowStalemate = !!gameState?.inStalemate

    if (!prevCheckmateRef.current && nowCheckmate) {
      const winner = gameState!.currentTurn === 'WHITE' ? 'Schwarz' : 'Weiß'
      toast.success(`Schachmatt — ${winner} gewinnt!`, {
        description: 'Die Partie ist beendet.',
        duration: 8000,
      })
    }

    if (!prevStalemateRef.current && nowStalemate) {
      toast('Patt — Unentschieden!', {
        description: 'Keine legalen Züge — die Partie endet remis.',
        duration: 8000,
      })
    }

    prevCheckmateRef.current = nowCheckmate
    prevStalemateRef.current = nowStalemate
  }, [gameState])

  // ── Load / refresh game state ─────────────────────────────────────────────
  const refreshGame = useCallback(async (id: string) => {
    try {
      const [state, movesResp, pgnResp] = await Promise.all([
        chessApi.getGame(id),
        chessApi.getLegalMoves(id),
        chessApi.getPgn(id),
      ])
      setGameState(state)
      setPosition(state.fen)
      setLegalMoves(movesResp.legalMoves)
      setPgn(pgnResp.pgn)
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        localStorage.removeItem(STORAGE_KEY)
        setGameId(null)
        setGameState(null)
        setLegalMoves([])
        setPgn('')
        setPosition(INITIAL_FEN)
        toast.error('Spiel nicht gefunden. Bitte neues Spiel starten.')
      } else {
        toast.error('Fehler beim Laden des Spiels.')
      }
    }
  }, [])

  useEffect(() => {
    if (gameId) {
      refreshGame(gameId)
    }
  }, [gameId, refreshGame])

  // ── New Game ──────────────────────────────────────────────────────────────
  const handleNewGame = useCallback(async (options: CreateGameRequest = {}) => {
    setShowGameSetup(false)
    setLoading(true)
    setPlayerColor(options.playerColor ?? 'white')
    setOpponentType(options.opponent ?? 'human')
    try {
      const created = await chessApi.createGame(options)
      localStorage.setItem(STORAGE_KEY, created.gameId)
      setGameId(created.gameId)
      setPosition(created.fen)
      setLegalMoves([])
      setPgn('')
      setGameState(null)
      setGameIdExpanded(false)
      setWhiteTime(clockSetting)
      setBlackTime(clockSetting)
      await refreshGame(created.gameId)
      toast.success('Neues Spiel gestartet!')
    } catch {
      toast.error('Spiel konnte nicht erstellt werden.')
    } finally {
      setLoading(false)
    }
  }, [refreshGame, clockSetting])

  // ── Make Move ─────────────────────────────────────────────────────────────
  const handleMove = useCallback(
    async (from: string, to: string, promotion?: string): Promise<boolean> => {
      if (!gameId) return false
      try {
        await chessApi.makeMove(gameId, { from, to, promotion })
        await refreshGame(gameId)
        return true
      } catch (err) {
        const msg =
          err instanceof ApiError ? err.message : 'Ungültiger Zug'
        toast.error(msg)
        return false
      }
    },
    [gameId, refreshGame],
  )

  // ── Undo ──────────────────────────────────────────────────────────────────
  const handleUndo = useCallback(async () => {
    if (!gameId) return
    setLoading(true)
    try {
      await chessApi.undo(gameId)
      await refreshGame(gameId)
      toast.success('Zug rückgängig gemacht')
    } catch {
      toast.error('Undo nicht möglich')
    } finally {
      setLoading(false)
    }
  }, [gameId, refreshGame])

  // ── Redo ──────────────────────────────────────────────────────────────────
  const handleRedo = useCallback(async () => {
    if (!gameId) return
    setLoading(true)
    try {
      await chessApi.redo(gameId)
      await refreshGame(gameId)
      toast.success('Zug wiederhergestellt')
    } catch {
      toast.error('Redo nicht möglich')
    } finally {
      setLoading(false)
    }
  }, [gameId, refreshGame])

  // ── Import FEN ────────────────────────────────────────────────────────────
  const handleImportFen = useCallback(
    async (fen: string) => {
      if (!gameId) {
        toast.error('Bitte zuerst ein neues Spiel starten')
        return
      }
      const result = await chessApi.importGame(gameId, { fen })
      if (!result.success) throw new Error(result.error ?? 'Import fehlgeschlagen')
      await refreshGame(gameId)
      toast.success('FEN erfolgreich geladen')
    },
    [gameId, refreshGame],
  )

  // ── Import PGN ────────────────────────────────────────────────────────────
  const handleImportPgn = useCallback(
    async (pgn: string) => {
      if (!gameId) {
        toast.error('Bitte zuerst ein neues Spiel starten')
        return
      }
      const result = await chessApi.importGame(gameId, { pgn })
      if (!result.success) throw new Error(result.error ?? 'Import fehlgeschlagen')
      await refreshGame(gameId)
      toast.success(`PGN geladen — ${result.moveCount ?? 0} Züge`)
    },
    [gameId, refreshGame],
  )

  // ── Logo / Home navigation ────────────────────────────────────────────────
  const handleLogoClick = useCallback(() => {
    if (gameId) {
      setShowHomeDialog(true)
    }
  }, [gameId])

  const handleClockApply = useCallback((seconds: number) => {
    setClockSetting(seconds)
    setWhiteTime(seconds)
    setBlackTime(seconds)
    setShowClockSettings(false)
  }, [])

  const goHome = useCallback(() => {
    setGameId(null)
    setGameState(null)
    setLegalMoves([])
    setPgn('')
    setPosition(INITIAL_FEN)
    setWhiteTime(clockSetting)
    setBlackTime(clockSetting)
    setShowHomeDialog(false)
  }, [clockSetting])

  const handleHomeSave = useCallback(() => {
    const content = pgn?.trim() ? pgn : position
    const ext = pgn?.trim() ? 'pgn' : 'fen'
    const mimeType = pgn?.trim() ? 'application/x-chess-pgn' : 'text/plain'
    const filename = `eja-chess-${gameId?.slice(0, 8) ?? 'game'}.${ext}`
    const blob = new Blob([content], { type: mimeType })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    goHome()
  }, [pgn, position, gameId, goHome])

  const handleHomeDiscard = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    goHome()
  }, [goHome])

  const currentFen = gameState?.fen ?? ''

  // ── Game ID chip ──────────────────────────────────────────────────────────
  const [gameIdExpanded, setGameIdExpanded] = useState(false)
  const [copied, setCopied] = useState(false)

  const handleGameIdClick = useCallback(() => {
    if (!gameId) return
    setGameIdExpanded((prev) => !prev)
    navigator.clipboard.writeText(gameId).then(() => {
      setCopied(true)
      toast('Game ID kopiert!', { description: gameId, duration: 3000 })
      setTimeout(() => setCopied(false), 2000)
    })
  }, [gameId])

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      <Toaster position="top-left" richColors theme="dark" />

      <HomeConfirmDialog
        open={showHomeDialog}
        onSave={handleHomeSave}
        onDiscard={handleHomeDiscard}
        onCancel={() => setShowHomeDialog(false)}
      />

      <LogModal open={showLog} pgn={pgn} onClose={() => setShowLog(false)} />

      <ClockSettingsModal
        open={showClockSettings}
        currentSeconds={clockSetting}
        onApply={handleClockApply}
        onCancel={() => setShowClockSettings(false)}
      />

      <GameSetupModal
        open={showGameSetup}
        onStart={handleNewGame}
        onCancel={() => setShowGameSetup(false)}
      />

      {/* ── Header ── */}
      <header className="border-b border-zinc-800 bg-zinc-900/80 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              onClick={handleLogoClick}
              disabled={!gameId}
              className="flex items-center gap-3 disabled:cursor-default group"
              data-testid="btn-logo"
              aria-label="Zurück zur Startseite"
            >
              <span className="text-2xl select-none">♟</span>
              <h1 className={`text-lg font-bold tracking-tight text-white ${gameId ? 'group-hover:text-zinc-300 transition-colors' : ''}`}>
                EJa Chess
              </h1>
            </button>
            {gameId && (
              <button
                onClick={handleGameIdClick}
                className="flex items-center gap-1 px-2 py-1 rounded-md bg-zinc-800 hover:bg-zinc-700 transition-colors group"
                title="Game ID kopieren"
                data-testid="btn-game-id"
              >
                {/* klein: nur Icon */}
                <span className="sm:hidden">
                  {copied
                    ? <Check className="h-3.5 w-3.5 text-green-400" />
                    : <Copy className="h-3.5 w-3.5 text-zinc-400 group-hover:text-zinc-200 transition-colors" />
                  }
                </span>
                {/* ab sm: ID-Text + Chevron + Icon */}
                <span className="hidden sm:flex items-center gap-1">
                  <ChevronRight
                    className={`h-3 w-3 text-zinc-500 transition-transform duration-200 ${gameIdExpanded ? 'rotate-90' : ''}`}
                  />
                  <span className="text-xs text-zinc-400 font-mono">
                    {gameIdExpanded ? gameId : `${gameId.slice(0, 8)}…`}
                  </span>
                  {copied
                    ? <Check className="h-3 w-3 text-green-400 ml-1" />
                    : <Copy className="h-3 w-3 text-zinc-600 group-hover:text-zinc-400 ml-1 transition-colors" />
                  }
                </span>
              </button>
            )}
          </div>

          <div className="flex items-center gap-2">
            <GameControls
              hasGame={!!gameId}
              loading={loading}
              onNewGame={() => setShowGameSetup(true)}
              onUndo={handleUndo}
              onRedo={handleRedo}
            />
            <ImportExportModal
              currentFen={currentFen}
              currentPgn={pgn}
              onImportFen={handleImportFen}
              onImportPgn={handleImportPgn}
            />
          </div>
        </div>
      </header>

      {/* ── Main content ── */}
      <main className="max-w-6xl mx-auto px-4 py-8">
        {!gameId ? (
          /* ── Welcome screen ── */
          <div className="flex flex-col items-center justify-center min-h-[60vh] gap-6 text-center">
            <div className="text-6xl select-none">♟</div>
            <div>
              <h2 className="text-2xl font-bold text-white mb-2">
                Willkommen bei EJa Chess
              </h2>
              <p className="text-zinc-400 max-w-sm">
                Starte ein neues Spiel, um zu beginnen. Du kannst jederzeit
                eine FEN- oder PGN-Stellung importieren.
              </p>
            </div>
            <button
              onClick={() => setShowGameSetup(true)}
              disabled={loading}
              data-testid="btn-start-welcome"
              className="px-8 py-3 bg-green-600 hover:bg-green-500 active:bg-green-700 text-white font-semibold rounded-xl transition-colors disabled:opacity-40 text-base"
            >
              {loading ? 'Wird gestartet…' : 'Neues Spiel starten'}
            </button>
          </div>
        ) : (
          /* ── Game layout ── */
          <div className="flex flex-col lg:flex-row gap-6 items-start justify-center">
            {/* Board */}
            <div className="flex-shrink-0 w-full lg:w-auto flex justify-center">
              <div style={{ width: 'min(100vw - 2rem, 560px)' }}>
                <ChessBoard
                  position={position}
                  legalMoves={isGameOver || isBotTurn ? [] : legalMoves}
                  disabled={loading || isGameOver || isBotTurn}
                  orientation={playerColor}
                  onMove={handleMove}
                />
              </div>
            </div>

            {/* Side panel */}
            <div className="w-full lg:w-64 xl:w-72 flex flex-col gap-4">
              {/* Clock */}
              <div className="rounded-xl bg-zinc-900 border border-zinc-800 p-4">
                <div className="flex items-center justify-between mb-3">
                  <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-500">
                    Schachuhr
                  </h2>
                  <button
                    onClick={() => setShowClockSettings(true)}
                    className="text-zinc-600 hover:text-zinc-300 transition-colors"
                    title="Uhr einstellen"
                    data-testid="btn-clock-settings"
                  >
                    <Settings2 className="h-4 w-4" />
                  </button>
                </div>
                <ChessClock
                  whiteTime={whiteTime}
                  blackTime={blackTime}
                  activePlayer={activePlayer}
                  isRunning={clockRunning}
                />
              </div>

              {gameState && (
                <div className="rounded-xl bg-zinc-900 border border-zinc-800 p-4">
                  <GameInfo gameState={gameState} />
                </div>
              )}

              {/* Move history — click to open full log */}
              <button
                className="rounded-xl bg-zinc-900 border border-zinc-800 p-4 text-left hover:border-zinc-600 transition-colors w-full"
                onClick={() => setShowLog(true)}
                title="Klicken für vollständiges Zugprotokoll"
                data-testid="btn-open-log"
              >
                <MoveHistory pgn={pgn} />
              </button>

              {isGameOver && (
                <div className="rounded-xl bg-zinc-800/60 border border-zinc-700 p-4 text-center">
                  <p className="text-zinc-400 text-sm mb-3">Partie beendet</p>
                  <button
                    onClick={() => setShowGameSetup(true)}
                    className="w-full px-4 py-2 bg-green-600 hover:bg-green-500 text-white font-medium rounded-lg transition-colors text-sm"
                  >
                    Neue Partie
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
