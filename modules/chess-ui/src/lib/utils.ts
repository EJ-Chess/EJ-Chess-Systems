import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

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
