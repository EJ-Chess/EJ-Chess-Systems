import { RotateCcw, RotateCw, Plus, Loader2 } from 'lucide-react'
import { Button } from './ui/button'

interface GameControlsProps {
  hasGame: boolean
  loading: boolean
  onNewGame: () => void
  onUndo: () => void
  onRedo: () => void
}

export function GameControls({
  hasGame,
  loading,
  onNewGame,
  onUndo,
  onRedo,
}: GameControlsProps) {
  return (
    <div className="flex items-center gap-2">
      <Button
        onClick={onNewGame}
        disabled={loading}
        size="sm"
        data-testid="btn-new-game"
      >
        {loading ? (
          <Loader2 className="h-4 w-4 animate-spin" />
        ) : (
          <Plus className="h-4 w-4" />
        )}
        Neues Spiel
      </Button>

      {hasGame && (
        <>
          <Button
            variant="outline"
            size="sm"
            onClick={onUndo}
            disabled={loading}
            title="Zug rückgängig (Undo)"
            data-testid="btn-undo"
          >
            <RotateCcw className="h-4 w-4" />
            Undo
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={onRedo}
            disabled={loading}
            title="Zug wiederherstellen (Redo)"
            data-testid="btn-redo"
          >
            <RotateCw className="h-4 w-4" />
            Redo
          </Button>
        </>
      )}
    </div>
  )
}
