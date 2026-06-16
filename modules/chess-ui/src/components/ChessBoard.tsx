import { useState, useCallback, CSSProperties } from 'react'
import { Chessboard } from 'react-chessboard'
import type { Square } from 'react-chessboard/dist/chessboard/types'
import type { MoveNotation } from '../api/chessApi'
import { isPawnPromotion } from '../lib/utils'

interface ChessBoardProps {
  position: string
  legalMoves: MoveNotation[]
  disabled: boolean
  orientation?: 'white' | 'black'
  /** When set, only pieces of this color can be selected or dragged. */
  playerColor?: 'white' | 'black'
  onMove: (from: string, to: string, promotion?: string) => Promise<boolean>
}

const SELECTED_SQUARE_STYLE: CSSProperties = {
  background: 'rgba(255, 255, 0, 0.45)',
}

const LEGAL_MOVE_STYLE: CSSProperties = {
  background: 'radial-gradient(circle, rgba(0,0,0,0.18) 25%, transparent 25%)',
}

const CAPTURE_STYLE: CSSProperties = {
  boxShadow: 'inset 0 0 0 4px rgba(220, 38, 38, 0.75)',
  borderRadius: '2px',
}

/** Returns a Set of squares that are occupied, parsed from the FEN board part. */
function occupiedSquares(fen: string): Set<string> {
  const occupied = new Set<string>()
  const ranks = fen.split(' ')[0].split('/')
  ranks.forEach((rank, rankIdx) => {
    let fileIdx = 0
    for (const ch of rank) {
      if (ch >= '1' && ch <= '8') {
        fileIdx += parseInt(ch, 10)
      } else {
        const file = String.fromCharCode('a'.charCodeAt(0) + fileIdx)
        const rankNum = 8 - rankIdx
        occupied.add(`${file}${rankNum}`)
        fileIdx++
      }
    }
  })
  return occupied
}

export function ChessBoard({
  position,
  legalMoves,
  disabled,
  orientation = 'white',
  playerColor,
  onMove,
}: ChessBoardProps) {
  const [selectedSquare, setSelectedSquare] = useState<string | null>(null)
  const [pendingPromotion, setPendingPromotion] = useState<{
    from: string
    to: string
  } | null>(null)

  /** Returns true when the piece belongs to the acting player (or no restriction set). */
  const isOwnPiece = useCallback(
    (pieceCode: string) => {
      if (!playerColor) return true
      const colorChar = playerColor === 'white' ? 'w' : 'b'
      return pieceCode.charAt(0) === colorChar
    },
    [playerColor],
  )

  const movesFromSquare = useCallback(
    (square: string) => legalMoves.filter((m) => m.from === square),
    [legalMoves],
  )

  const customSquareStyles = useCallback((): Record<string, CSSProperties> => {
    const styles: Record<string, CSSProperties> = {}
    if (!selectedSquare) return styles

    styles[selectedSquare] = SELECTED_SQUARE_STYLE

    const occupied = occupiedSquares(position)
    movesFromSquare(selectedSquare).forEach((move) => {
      styles[move.to] = occupied.has(move.to) ? CAPTURE_STYLE : LEGAL_MOVE_STYLE
    })

    return styles
  }, [selectedSquare, movesFromSquare, position])

  const handleSquareClick = useCallback(
    async (square: string) => {
      if (disabled) return

      if (selectedSquare) {
        const movesToTarget = legalMoves.filter(
          (m) => m.from === selectedSquare && m.to === square,
        )

        if (movesToTarget.length > 0) {
          setSelectedSquare(null)
          if (isPawnPromotion(position, selectedSquare, square)) {
            setPendingPromotion({ from: selectedSquare, to: square })
          } else {
            await onMove(selectedSquare, square)
          }
          return
        }
      }

      const fromMoves = movesFromSquare(square)
      if (fromMoves.length > 0) {
        setSelectedSquare(square)
      } else {
        setSelectedSquare(null)
      }
    },
    [disabled, selectedSquare, legalMoves, movesFromSquare, onMove],
  )

  // react-chessboard expects synchronous boolean from onPieceDrop.
  // We return false (piece snaps back) and drive position through server state.
  const handlePieceDrop = useCallback(
    (sourceSquare: string, targetSquare: string, piece: string): boolean => {
      if (disabled || !isOwnPiece(piece)) return false
      setSelectedSquare(null)

      const isLegal = legalMoves.some(
        (m) => m.from === sourceSquare && m.to === targetSquare,
      )
      if (!isLegal) return false

      if (isPawnPromotion(position, sourceSquare, targetSquare)) {
        // react-chessboard will show its promotion modal and call
        // onPromotionPieceSelect with the chosen piece + square params
        return false
      }

      // Fire-and-forget: position updates via parent state after API responds
      void onMove(sourceSquare, targetSquare)
      return false
    },
    [disabled, legalMoves, onMove],
  )

  // react-chessboard expects synchronous boolean from onPromotionPieceSelect.
  // For drag-and-drop react-chessboard passes promoteFromSquare / promoteToSquare directly.
  // For click navigation we use our own pendingPromotion state.
  const handlePromotionPieceSelect = useCallback(
    (piece?: string, promoteFromSquare?: string, promoteToSquare?: string): boolean => {
      const from = pendingPromotion?.from ?? promoteFromSquare
      const to   = pendingPromotion?.to   ?? promoteToSquare
      if (!from || !to || !piece) {
        setPendingPromotion(null)
        return false
      }
      const promotion = piece.charAt(1).toUpperCase()
      void onMove(from, to, promotion)
      setPendingPromotion(null)
      return false
    },
    [pendingPromotion, onMove],
  )

  return (
    <div className="rounded-xl overflow-hidden shadow-2xl" data-testid="chess-board-wrapper">
      <Chessboard
        id="main-board"
        position={position}
        onSquareClick={handleSquareClick}
        onPieceDrop={handlePieceDrop}
        onPromotionPieceSelect={handlePromotionPieceSelect}
        showPromotionDialog={pendingPromotion !== null}
        promotionToSquare={(pendingPromotion?.to ?? undefined) as Square | undefined}
        customSquareStyles={customSquareStyles()}
        boardOrientation={orientation}
        arePiecesDraggable={!disabled}
        isDraggablePiece={({ piece }) => !disabled && isOwnPiece(piece)}
        promotionDialogVariant="modal"
        customDarkSquareStyle={{ backgroundColor: '#769656' }}
        customLightSquareStyle={{ backgroundColor: '#eeeed2' }}
        customBoardStyle={{
          borderRadius: '8px',
          boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
        }}
      />
    </div>
  )
}
