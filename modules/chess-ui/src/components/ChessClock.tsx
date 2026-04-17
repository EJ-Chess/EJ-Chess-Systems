import { CSSProperties } from 'react'

interface ChessClockProps {
  whiteTime: number
  blackTime: number
  activePlayer: 'WHITE' | 'BLACK' | null
  isRunning: boolean
}

function formatTime(seconds: number): string {
  const m = Math.floor(Math.max(seconds, 0) / 60)
  const s = Math.max(seconds, 0) % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function ClockFace({
  label,
  time,
  active,
}: {
  label: string
  time: number
  active: boolean
}) {
  const low = time <= 30
  const critical = time <= 10

  const containerStyle: CSSProperties = {
    transition: 'box-shadow 0.2s',
    boxShadow: active ? '0 0 0 2px #86efac' : 'none',
  }

  return (
    <div
      className={`flex-1 rounded-xl px-4 py-3 flex flex-col items-center gap-1 border transition-colors
        ${active ? 'bg-zinc-800 border-green-400/40' : 'bg-zinc-900 border-zinc-700'}`}
      style={containerStyle}
      data-testid={`clock-${label.toLowerCase()}`}
    >
      <span className="text-xs font-semibold uppercase tracking-wider text-zinc-500">
        {label}
      </span>
      <span
        className={`text-2xl font-mono font-bold tabular-nums transition-colors
          ${critical ? 'text-red-400 animate-pulse' : low ? 'text-orange-400' : active ? 'text-green-300' : 'text-zinc-300'}`}
      >
        {formatTime(time)}
      </span>
    </div>
  )
}

export function ChessClock({ whiteTime, blackTime, activePlayer, isRunning }: ChessClockProps) {
  return (
    <div className="flex gap-2" data-testid="chess-clock">
      <ClockFace
        label="Schwarz"
        time={blackTime}
        active={isRunning && activePlayer === 'BLACK'}
      />
      <ClockFace
        label="Weiß"
        time={whiteTime}
        active={isRunning && activePlayer === 'WHITE'}
      />
    </div>
  )
}
