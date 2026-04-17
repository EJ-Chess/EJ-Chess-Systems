import { Badge } from './ui/badge'
import type { GameStateResponse } from '../api/chessApi'

interface GameInfoProps {
  gameState: GameStateResponse
}

export function GameInfo({ gameState }: GameInfoProps) {
  const { currentTurn, fullmoveNumber, halfmoveClock, inCheck, inCheckmate, inStalemate } =
    gameState

  const statusBadge = () => {
    if (inCheckmate)
      return (
        <Badge variant="destructive" className="text-sm px-3 py-1">
          Schachmatt — {currentTurn === 'WHITE' ? 'Schwarz' : 'Weiß'} gewinnt
        </Badge>
      )
    if (inStalemate)
      return (
        <Badge variant="warning" className="text-sm px-3 py-1">
          Patt — Remis
        </Badge>
      )
    if (inCheck)
      return (
        <Badge variant="warning" className="text-sm px-3 py-1">
          Schach!
        </Badge>
      )
    return null
  }

  const turnColor = currentTurn === 'WHITE' ? '#eeeed2' : '#1a1a1a'
  const turnLabel = currentTurn === 'WHITE' ? 'Weiß' : 'Schwarz'

  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-500">
        Spielstatus
      </h2>

      <div className="flex items-center gap-3">
        <div
          className="h-5 w-5 rounded-full border border-zinc-500 flex-shrink-0"
          style={{ backgroundColor: turnColor }}
          aria-label={`${turnLabel} ist am Zug`}
        />
        <span className="text-base font-semibold text-white">
          {turnLabel} ist am Zug
        </span>
      </div>

      {statusBadge() && <div>{statusBadge()}</div>}

      <div className="grid grid-cols-2 gap-2 text-sm">
        <div className="rounded-lg bg-zinc-800/60 px-3 py-2">
          <p className="text-xs text-zinc-500 mb-0.5">Vollzüge</p>
          <p className="font-semibold text-white">{fullmoveNumber}</p>
        </div>
        <div className="rounded-lg bg-zinc-800/60 px-3 py-2">
          <p className="text-xs text-zinc-500 mb-0.5">50-Züge-Regel</p>
          <p className="font-semibold text-white">{halfmoveClock} / 50</p>
        </div>
      </div>
    </div>
  )
}
