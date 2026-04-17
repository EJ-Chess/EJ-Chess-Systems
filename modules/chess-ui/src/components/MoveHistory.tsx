import { useEffect, useRef } from 'react'
import { parsePgnToMoves } from '../lib/utils'

interface MoveHistoryProps {
  pgn: string
}

export function MoveHistory({ pgn }: MoveHistoryProps) {
  const moves = parsePgnToMoves(pgn)
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [pgn])

  return (
    <div className="flex flex-col gap-2">
      <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-500">
        Zughistorie
      </h2>

      <div className="max-h-48 overflow-y-auto rounded-lg bg-zinc-800/40 p-2">
        {moves.length === 0 ? (
          <p className="text-center text-xs text-zinc-600 py-4">
            Noch keine Züge gespielt
          </p>
        ) : (
          <table className="w-full text-sm">
            <tbody>
              {moves.map((move, idx) => (
                <tr
                  key={idx}
                  className="group hover:bg-zinc-700/30 rounded"
                >
                  <td className="py-0.5 pl-2 pr-3 text-zinc-500 font-mono text-xs w-8">
                    {idx + 1}.
                  </td>
                  <td className="py-0.5 pr-3 font-mono text-zinc-200 w-1/2">
                    {move.white}
                  </td>
                  <td className="py-0.5 font-mono text-zinc-400 w-1/2">
                    {move.black ?? ''}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div ref={endRef} />
      </div>
    </div>
  )
}
