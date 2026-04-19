import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from './ui/dialog'
import { Button } from './ui/button'
import type { CreateGameRequest } from '../api/chessApi'

interface GameSetupModalProps {
  open: boolean
  onStart: (options: CreateGameRequest) => void
  onCancel: () => void
}

const ELO_PRESETS = [
  { label: '800', value: 800, hint: 'Einsteiger' },
  { label: '1000', value: 1000, hint: 'Anfänger' },
  { label: '1200', value: 1200, hint: 'Mittelfeld' },
  { label: '1400', value: 1400, hint: 'Fortg.' },
  { label: '1600', value: 1600, hint: 'Erfahren' },
  { label: '1800', value: 1800, hint: 'Experte' },
  { label: '2000', value: 2000, hint: 'Meister' },
]

export function GameSetupModal({ open, onStart, onCancel }: GameSetupModalProps) {
  const [opponent, setOpponent] = useState<'human' | 'bot'>('human')
  const [playerColor, setPlayerColor] = useState<'white' | 'black'>('white')
  const [botElo, setBotElo] = useState(1400)

  function handleStart() {
    if (opponent === 'bot') {
      onStart({ opponent: 'bot', playerColor, botElo })
    } else {
      onStart({ opponent: 'human' })
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onCancel() }}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Neues Spiel</DialogTitle>
          <DialogDescription>
            Wähle deinen Gegner und die Spieleinstellungen.
          </DialogDescription>
        </DialogHeader>

        {/* Opponent selection */}
        <div>
          <p className="text-xs text-zinc-500 uppercase tracking-wider mb-2 font-semibold">Gegner</p>
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => setOpponent('human')}
              data-testid="btn-opponent-human"
              className={`rounded-lg px-3 py-3 text-sm font-medium border transition-colors flex flex-col items-center gap-1
                ${opponent === 'human'
                  ? 'bg-green-700 border-green-500 text-white'
                  : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'}`}
            >
              <span className="text-xl">👤</span>
              Mensch
            </button>
            <button
              onClick={() => setOpponent('bot')}
              data-testid="btn-opponent-bot"
              className={`rounded-lg px-3 py-3 text-sm font-medium border transition-colors flex flex-col items-center gap-1
                ${opponent === 'bot'
                  ? 'bg-green-700 border-green-500 text-white'
                  : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'}`}
            >
              <span className="text-xl">🤖</span>
              Bot
            </button>
          </div>
        </div>

        {/* Bot options */}
        {opponent === 'bot' && (
          <>
            {/* Color selection */}
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-wider mb-2 font-semibold">Deine Farbe</p>
              <div className="grid grid-cols-2 gap-2">
                <button
                  onClick={() => setPlayerColor('white')}
                  data-testid="btn-color-white"
                  className={`rounded-lg px-3 py-2 text-sm font-medium border transition-colors flex items-center justify-center gap-2
                    ${playerColor === 'white'
                      ? 'bg-green-700 border-green-500 text-white'
                      : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'}`}
                >
                  <span className="text-lg">♔</span>
                  Weiß
                </button>
                <button
                  onClick={() => setPlayerColor('black')}
                  data-testid="btn-color-black"
                  className={`rounded-lg px-3 py-2 text-sm font-medium border transition-colors flex items-center justify-center gap-2
                    ${playerColor === 'black'
                      ? 'bg-green-700 border-green-500 text-white'
                      : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'}`}
                >
                  <span className="text-lg">♚</span>
                  Schwarz
                </button>
              </div>
            </div>

            {/* ELO selection */}
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-wider mb-2 font-semibold">
                Bot-Stärke — ELO {botElo}
              </p>
              <div className="grid grid-cols-4 gap-1.5">
                {ELO_PRESETS.map((p) => (
                  <button
                    key={p.value}
                    onClick={() => setBotElo(p.value)}
                    data-testid={`btn-elo-${p.value}`}
                    title={p.hint}
                    className={`rounded-lg px-2 py-2 text-xs font-medium border transition-colors text-center
                      ${botElo === p.value
                        ? 'bg-green-700 border-green-500 text-white'
                        : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'}`}
                  >
                    <div>{p.label}</div>
                    <div className="text-zinc-400 text-[10px] mt-0.5 leading-tight">{p.hint}</div>
                  </button>
                ))}
              </div>
            </div>
          </>
        )}

        <DialogFooter className="gap-2 sm:gap-0 mt-1">
          <Button variant="outline" onClick={onCancel} data-testid="btn-setup-cancel">
            Abbrechen
          </Button>
          <Button onClick={handleStart} data-testid="btn-setup-start">
            Starten
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
