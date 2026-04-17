import { useState } from 'react'
import { Upload, Download, Copy, Check } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from './ui/dialog'
import { Tabs, TabsList, TabsTrigger, TabsContent } from './ui/tabs'
import { Button } from './ui/button'

interface ImportExportModalProps {
  currentFen: string
  currentPgn: string
  onImportFen: (fen: string) => Promise<void>
  onImportPgn: (pgn: string) => Promise<void>
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    await navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Button variant="ghost" size="icon" onClick={handleCopy} title="Kopieren" data-testid="copy-button">
      {copied ? (
        <Check className="h-4 w-4 text-green-400" />
      ) : (
        <Copy className="h-4 w-4" />
      )}
    </Button>
  )
}

export function ImportExportModal({
  currentFen,
  currentPgn,
  onImportFen,
  onImportPgn,
}: ImportExportModalProps) {
  const [open, setOpen] = useState(false)
  const [fenInput, setFenInput] = useState('')
  const [pgnInput, setPgnInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleImportFen = async () => {
    if (!fenInput.trim()) return
    setLoading(true)
    setError(null)
    try {
      await onImportFen(fenInput.trim())
      setFenInput('')
      setOpen(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Import fehlgeschlagen')
    } finally {
      setLoading(false)
    }
  }

  const handleImportPgn = async () => {
    if (!pgnInput.trim()) return
    setLoading(true)
    setError(null)
    try {
      await onImportPgn(pgnInput.trim())
      setPgnInput('')
      setOpen(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Import fehlgeschlagen')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm" data-testid="btn-import-export">
          <Upload className="h-4 w-4" />
          Import / Export
        </Button>
      </DialogTrigger>

      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Download className="h-5 w-5 text-green-400" />
            Import & Export
          </DialogTitle>
        </DialogHeader>

        <Tabs defaultValue="fen">
          <TabsList className="w-full">
            <TabsTrigger value="fen" className="flex-1">
              FEN
            </TabsTrigger>
            <TabsTrigger value="pgn" className="flex-1">
              PGN
            </TabsTrigger>
          </TabsList>

          {/* ── FEN Tab ── */}
          <TabsContent value="fen" className="space-y-3">
            <div>
              <p className="text-xs text-zinc-500 mb-1">Aktuelle Stellung (FEN)</p>
              <div className="flex items-center gap-1 rounded-lg bg-zinc-800 px-3 py-2">
                <code className="flex-1 text-xs text-green-300 break-all font-mono" data-testid="fen-export-value">
                  {currentFen || '—'}
                </code>
                {currentFen && <CopyButton text={currentFen} />}
              </div>
            </div>

            <div>
              <p className="text-xs text-zinc-500 mb-1">FEN importieren</p>
              <textarea
                className="w-full rounded-lg bg-zinc-800 border border-zinc-700 px-3 py-2 text-xs font-mono text-zinc-200 placeholder:text-zinc-600 focus:outline-none focus:ring-2 focus:ring-green-500 resize-none"
                rows={3}
                placeholder="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                value={fenInput}
                onChange={(e) => setFenInput(e.target.value)}
                data-testid="fen-import-input"
              />
              <Button
                className="mt-2 w-full"
                onClick={handleImportFen}
                disabled={loading || !fenInput.trim()}
                data-testid="btn-import-fen"
              >
                <Upload className="h-4 w-4" />
                FEN laden
              </Button>
            </div>
          </TabsContent>

          {/* ── PGN Tab ── */}
          <TabsContent value="pgn" className="space-y-3">
            <div>
              <p className="text-xs text-zinc-500 mb-1">Aktuelle Partie (PGN)</p>
              <div className="flex items-start gap-1 rounded-lg bg-zinc-800 px-3 py-2 min-h-[60px]">
                <code className="flex-1 text-xs text-green-300 whitespace-pre-wrap font-mono" data-testid="pgn-export-value">
                  {currentPgn || '—'}
                </code>
                {currentPgn && <CopyButton text={currentPgn} />}
              </div>
            </div>

            <div>
              <p className="text-xs text-zinc-500 mb-1">PGN importieren</p>
              <textarea
                className="w-full rounded-lg bg-zinc-800 border border-zinc-700 px-3 py-2 text-xs font-mono text-zinc-200 placeholder:text-zinc-600 focus:outline-none focus:ring-2 focus:ring-green-500 resize-none"
                rows={5}
                placeholder="1. e4 e5 2. Nf3 Nc6 3. Bb5 a6"
                value={pgnInput}
                onChange={(e) => setPgnInput(e.target.value)}
                data-testid="pgn-import-input"
              />
              <Button
                className="mt-2 w-full"
                onClick={handleImportPgn}
                disabled={loading || !pgnInput.trim()}
                data-testid="btn-import-pgn"
              >
                <Upload className="h-4 w-4" />
                PGN laden
              </Button>
            </div>
          </TabsContent>
        </Tabs>

        {error && (
          <p className="text-xs text-red-400 bg-red-900/20 rounded-lg px-3 py-2" role="alert" data-testid="import-error">
            {error}
          </p>
        )}
      </DialogContent>
    </Dialog>
  )
}
