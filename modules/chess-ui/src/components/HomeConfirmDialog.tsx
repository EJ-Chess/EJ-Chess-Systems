import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from './ui/dialog'
import { Button } from './ui/button'

interface HomeConfirmDialogProps {
  open: boolean
  onSave: () => void
  onDiscard: () => void
  onCancel: () => void
}

export function HomeConfirmDialog({
  open,
  onSave,
  onDiscard,
  onCancel,
}: HomeConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onCancel() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Spiel verlassen?</DialogTitle>
          <DialogDescription>
            Möchtest du die aktuelle Session speichern? Du kannst dann später weiterspielen.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button variant="outline" onClick={onCancel}>
            Abbrechen
          </Button>
          <Button variant="outline" onClick={onDiscard} className="text-red-400 border-red-800 hover:bg-red-950">
            Nein, verwerfen
          </Button>
          <Button onClick={onSave}>
            Ja, speichern
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
