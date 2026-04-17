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

interface ClockSettingsModalProps {
  open: boolean
  currentSeconds: number
  onApply: (seconds: number) => void
  onCancel: () => void
}

const PRESETS = [
  { label: '1 min', seconds: 60 },
  { label: '3 min', seconds: 3 * 60 },
  { label: '5 min', seconds: 5 * 60 },
  { label: '10 min', seconds: 10 * 60 },
  { label: '15 min', seconds: 15 * 60 },
  { label: '30 min', seconds: 30 * 60 },
]

export function ClockSettingsModal({
  open,
  currentSeconds,
  onApply,
  onCancel,
}: ClockSettingsModalProps) {
  const [minutes, setMinutes] = useState(() => Math.floor(currentSeconds / 60))
  const [seconds, setSeconds] = useState(() => currentSeconds % 60)

  const total = minutes * 60 + seconds
  const isValid = total > 0 && total <= 60 * 60

  function applyPreset(s: number) {
    setMinutes(Math.floor(s / 60))
    setSeconds(s % 60)
  }

  function handleMinutes(v: string) {
    const n = Math.max(0, Math.min(60, parseInt(v) || 0))
    setMinutes(n)
  }

  function handleSeconds(v: string) {
    const n = Math.max(0, Math.min(59, parseInt(v) || 0))
    setSeconds(n)
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onCancel() }}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Schachuhr einstellen</DialogTitle>
          <DialogDescription>
            Wähle eine Vorgabe oder gib eine eigene Zeit ein. Beide Uhren werden zurückgesetzt.
          </DialogDescription>
        </DialogHeader>

        {/* Presets */}
        <div className="grid grid-cols-3 gap-2">
          {PRESETS.map((p) => (
            <button
              key={p.seconds}
              onClick={() => applyPreset(p.seconds)}
              className={`rounded-lg px-3 py-2 text-sm font-medium border transition-colors
                ${total === p.seconds
                  ? 'bg-green-700 border-green-500 text-white'
                  : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'}`}
              data-testid={`preset-${p.seconds}`}
            >
              {p.label}
            </button>
          ))}
        </div>

        {/* Custom input */}
        <div className="flex items-center gap-3 mt-1">
          <div className="flex-1">
            <label className="text-xs text-zinc-500 mb-1 block">Minuten</label>
            <input
              type="number"
              min={0}
              max={60}
              value={minutes}
              onChange={(e) => handleMinutes(e.target.value)}
              className="w-full rounded-lg bg-zinc-800 border border-zinc-700 px-3 py-2 text-white text-sm focus:outline-none focus:border-zinc-500"
              data-testid="input-minutes"
            />
          </div>
          <span className="text-zinc-400 mt-5 text-lg font-bold">:</span>
          <div className="flex-1">
            <label className="text-xs text-zinc-500 mb-1 block">Sekunden</label>
            <input
              type="number"
              min={0}
              max={59}
              value={seconds}
              onChange={(e) => handleSeconds(e.target.value)}
              className="w-full rounded-lg bg-zinc-800 border border-zinc-700 px-3 py-2 text-white text-sm focus:outline-none focus:border-zinc-500"
              data-testid="input-seconds"
            />
          </div>
        </div>

        <DialogFooter className="gap-2 sm:gap-0 mt-1">
          <Button variant="outline" onClick={onCancel}>
            Abbrechen
          </Button>
          <Button onClick={() => onApply(total)} disabled={!isValid} data-testid="btn-apply-clock">
            Übernehmen
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
