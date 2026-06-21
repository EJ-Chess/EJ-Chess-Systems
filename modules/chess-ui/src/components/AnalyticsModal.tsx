import { useState, useEffect, useRef } from 'react'
import { BarChart2, Play, Loader2, Trophy } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from './ui/dialog'
import { Button } from './ui/button'
import { analyticsApi, type AnalyticsResult, type AnalyticsStatus, type AnalyticsSource } from '../api/chessApi'

const POLL_INTERVAL_MS = 1500

export function AnalyticsModal() {
  const [open, setOpen] = useState(false)
  const [source, setSource] = useState<AnalyticsSource>('local')
  const [result, setResult] = useState<AnalyticsResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const status: AnalyticsStatus = result?.status ?? 'IDLE'
  const isRunning = status === 'RUNNING'

  // Stop polling when modal closes
  useEffect(() => {
    if (!open && pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }, [open])

  // Cleanup on unmount
  useEffect(() => () => {
    if (pollRef.current) clearInterval(pollRef.current)
  }, [])

  const startPolling = () => {
    if (pollRef.current) return
    pollRef.current = setInterval(async () => {
      try {
        const latest = await analyticsApi.getResults()
        setResult(latest)
        if (latest.status === 'DONE' || latest.status === 'ERROR') {
          clearInterval(pollRef.current!)
          pollRef.current = null
        }
      } catch {
        // polling error — keep trying
      }
    }, POLL_INTERVAL_MS)
  }

  const handleRun = async () => {
    setError(null)
    try {
      await analyticsApi.run(source)
      setResult((prev) => prev ? { ...prev, status: 'RUNNING', dataSource: source } : {
        status: 'RUNNING',
        runAt: null,
        victoriesPerPlayer: [],
        winsPerColor: [],
        avgEloBeatPerPlayer: [],
        bestPlayer: '',
        dataSource: source,
      })
      startPolling()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Analytics-Service nicht erreichbar')
    }
  }

  // On open: fetch current status immediately
  const handleOpenChange = async (next: boolean) => {
    setOpen(next)
    if (next) {
      try {
        const current = await analyticsApi.getResults()
        setResult(current)
        if (current.status === 'RUNNING') startPolling()
      } catch {
        // service not available yet — show IDLE state
      }
    }
  }

  const activeSource = result?.dataSource || source
  const eloLabel = activeSource === 'lichess'
    ? 'Ø Gegner-ELO besiegt'
    : 'Ø Bot-ELO besiegt (gewonnene Partien)'

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm" data-testid="btn-analytics">
          <BarChart2 className="h-4 w-4" />
          Analytics
        </Button>
      </DialogTrigger>

      <DialogContent className="sm:max-w-2xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <BarChart2 className="h-5 w-5 text-green-400" />
            Spark Chess Analytics
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* Source selection */}
          <div>
            <p className="text-xs text-zinc-500 uppercase tracking-wider mb-2 font-semibold">Datenquelle</p>
            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => setSource('local')}
                disabled={isRunning}
                data-testid="btn-source-local"
                className={`rounded-lg px-3 py-2 text-sm font-medium border transition-colors flex flex-col items-center gap-1 disabled:opacity-50
                  ${source === 'local'
                    ? 'bg-green-700 border-green-500 text-white'
                    : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'}`}
              >
                <span className="text-base">🏠</span>
                Lokale Partien
              </button>
              <button
                onClick={() => setSource('lichess')}
                disabled={isRunning}
                data-testid="btn-source-lichess"
                className={`rounded-lg px-3 py-2 text-sm font-medium border transition-colors flex flex-col items-center gap-1 disabled:opacity-50
                  ${source === 'lichess'
                    ? 'bg-green-700 border-green-500 text-white'
                    : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'}`}
              >
                <span className="text-base">♟️</span>
                Lichess (121k Partien)
              </button>
            </div>
          </div>

          {/* Run button + status */}
          <div className="flex items-center justify-between">
            <div className="text-sm text-zinc-400">
              {status === 'IDLE' && 'Noch keine Analyse durchgeführt.'}
              {status === 'RUNNING' && (
                <span className="flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin text-green-400" />
                  Spark-Job läuft…
                  {result?.dataSource === 'lichess' && (
                    <span className="text-xs text-zinc-500">(121k Partien, ca. 30–60s)</span>
                  )}
                </span>
              )}
              {status === 'DONE' && result?.runAt && (
                <span className="text-zinc-500 text-xs">
                  Zuletzt: {new Date(result.runAt).toLocaleString('de-DE')}
                  {result.dataSource && (
                    <span className="ml-2 px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-400">
                      {result.dataSource === 'lichess' ? 'Lichess' : 'Lokal'}
                    </span>
                  )}
                </span>
              )}
              {status === 'ERROR' && (
                <span className="text-red-400 text-xs">Fehler: {result?.bestPlayer}</span>
              )}
            </div>

            <Button
              onClick={handleRun}
              disabled={isRunning}
              size="sm"
              data-testid="btn-run-analytics"
            >
              {isRunning
                ? <Loader2 className="h-4 w-4 animate-spin" />
                : <Play className="h-4 w-4" />}
              {isRunning ? 'Läuft…' : 'Analyse starten'}
            </Button>
          </div>

          {error && (
            <p className="text-xs text-red-400 bg-red-900/20 rounded-lg px-3 py-2" role="alert">
              {error}
            </p>
          )}

          {/* Results */}
          {status === 'DONE' && result && (
            <div className="space-y-5">

              {/* Best player highlight */}
              <div className="flex items-center gap-3 rounded-xl bg-green-900/20 border border-green-800/40 px-4 py-3">
                <Trophy className="h-5 w-5 text-yellow-400 flex-shrink-0" />
                <div>
                  <p className="text-xs text-zinc-500 uppercase tracking-wider">Bester Spieler</p>
                  <p className="text-base font-bold text-white">{result.bestPlayer}</p>
                </div>
              </div>

              {/* Victories per player */}
              <ResultTable
                title="Siege pro Spieler"
                columns={['Spieler', 'Siege']}
                rows={result.victoriesPerPlayer.map((r) => [r.player, String(r.victories)])}
              />

              {/* Wins per color */}
              <ResultTable
                title="Siege nach Farbe"
                columns={['Farbe', 'Siege gesamt']}
                rows={result.winsPerColor.map((r) => [r.color, String(r.totalWins)])}
              />

              {/* Avg ELO beaten */}
              {result.avgEloBeatPerPlayer.length > 0 && (
                <ResultTable
                  title={eloLabel}
                  columns={['Spieler', 'Ø ELO']}
                  rows={result.avgEloBeatPerPlayer.map((r) => [
                    r.player,
                    r.avgEloBeat.toFixed(1),
                  ])}
                />
              )}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

function ResultTable({
  title,
  columns,
  rows,
}: {
  title: string
  columns: [string, string]
  rows: [string, string][]
}) {
  return (
    <div>
      <p className="text-xs font-semibold uppercase tracking-wider text-zinc-500 mb-2">{title}</p>
      <div className="rounded-lg overflow-hidden border border-zinc-800">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-zinc-800/60">
              <th className="text-left px-3 py-2 text-zinc-400 font-medium">{columns[0]}</th>
              <th className="text-right px-3 py-2 text-zinc-400 font-medium">{columns[1]}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([a, b], i) => (
              <tr
                key={i}
                className={i % 2 === 0 ? 'bg-zinc-900' : 'bg-zinc-900/60'}
              >
                <td className="px-3 py-2 text-zinc-200">{a}</td>
                <td className="px-3 py-2 text-right text-green-300 font-mono">{b}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
