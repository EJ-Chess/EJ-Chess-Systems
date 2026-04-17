import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from './ui/dialog'
import { parsePgnToMoves } from '../lib/utils'

interface LogModalProps {
  open: boolean
  pgn: string
  onClose: () => void
}

export function LogModal({ open, pgn, onClose }: LogModalProps) {
  const moves = parsePgnToMoves(pgn)

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Zugprotokoll</DialogTitle>
          <DialogDescription>
            Alle bisher gespielten Züge dieser Partie
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-96 overflow-y-auto rounded-lg bg-zinc-800/40 p-2">
          {moves.length === 0 ? (
            <p className="text-center text-sm text-zinc-500 py-6">
              Noch keine Züge gespielt
            </p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-700">
                  <th className="pb-1 pl-2 pr-3 text-left text-xs text-zinc-500 font-normal w-8">#</th>
                  <th className="pb-1 pr-3 text-left text-xs text-zinc-500 font-normal w-1/2">Weiß</th>
                  <th className="pb-1 text-left text-xs text-zinc-500 font-normal w-1/2">Schwarz</th>
                </tr>
              </thead>
              <tbody>
                {moves.map((move, idx) => (
                  <tr key={idx} className="hover:bg-zinc-700/30">
                    <td className="py-1 pl-2 pr-3 text-zinc-500 font-mono text-xs">{idx + 1}.</td>
                    <td className="py-1 pr-3 font-mono text-zinc-200">{move.white}</td>
                    <td className="py-1 font-mono text-zinc-400">{move.black ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
