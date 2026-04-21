import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Returns true when moving a pawn from `from` to `to` constitutes a promotion.
 * Reads the piece type directly from the FEN board string — no external library needed.
 */
export function isPawnPromotion(fen: string, from: string, to: string): boolean {
  const boardPart = fen.split(' ')[0]
  const ranks = boardPart.split('/')
  const file = from.charCodeAt(0) - 97         // 'a' → 0, 'h' → 7
  const rank = parseInt(from[1], 10) - 1        // '1' → 0, '8' → 7
  const rankIdx = 7 - rank                      // FEN rank 8 is array index 0

  if (rankIdx < 0 || rankIdx > 7) return false

  let fileIdx = 0
  for (const ch of ranks[rankIdx]) {
    if (ch >= '1' && ch <= '8') {
      fileIdx += parseInt(ch, 10)
    } else {
      if (fileIdx === file) {
        const toRank = to[1]
        return (ch === 'P' && toRank === '8') || (ch === 'p' && toRank === '1')
      }
      fileIdx++
    }
  }
  return false
}

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function parsePgnToMoves(pgn: string): { white: string; black?: string }[] {
  if (!pgn.trim()) return []
  // Strip headers [Key "Value"] and result markers
  const cleaned = pgn
    .replace(/\[.*?\]/g, '')
    .replace(/\d-\d|\*|1\/2-1\/2/g, '')
    .trim()
  const tokens = cleaned.split(/\s+/).filter(Boolean)
  const moves: { white: string; black?: string }[] = []
  let i = 0
  while (i < tokens.length) {
    // Skip move numbers like "1." "12."
    if (/^\d+\./.test(tokens[i])) {
      i++
      continue
    }
    const white = tokens[i++]
    const black = tokens[i] && !/^\d+\./.test(tokens[i]) ? tokens[i++] : undefined
    moves.push({ white, black })
  }
  return moves
}
