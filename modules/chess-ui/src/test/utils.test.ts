import { describe, it, expect } from 'vitest'
import { parsePgnToMoves, cn, isPawnPromotion } from '../lib/utils'

describe('cn', () => {
  it('merges class names', () => {
    expect(cn('a', 'b')).toBe('a b')
  })

  it('deduplicates conflicting tailwind classes', () => {
    expect(cn('px-2', 'px-4')).toBe('px-4')
  })
})

describe('parsePgnToMoves', () => {
  it('returns empty array for empty pgn', () => {
    expect(parsePgnToMoves('')).toEqual([])
    expect(parsePgnToMoves('   ')).toEqual([])
  })

  it('parses simple opening moves', () => {
    const moves = parsePgnToMoves('1. e4 e5 2. Nf3 Nc6')
    expect(moves).toHaveLength(2)
    expect(moves[0]).toEqual({ white: 'e4', black: 'e5' })
    expect(moves[1]).toEqual({ white: 'Nf3', black: 'Nc6' })
  })

  it('handles final half-move (White played last)', () => {
    const moves = parsePgnToMoves('1. e4 e5 2. Nf3')
    expect(moves).toHaveLength(2)
    expect(moves[1]).toEqual({ white: 'Nf3', black: undefined })
  })

  it('handles castling notation', () => {
    const moves = parsePgnToMoves('1. e4 e5 2. O-O O-O-O')
    expect(moves[1]).toEqual({ white: 'O-O', black: 'O-O-O' })
  })

  it('strips PGN result markers', () => {
    const moves = parsePgnToMoves('1. e4 e5 1-0')
    expect(moves).toHaveLength(1)
    expect(moves[0]).toEqual({ white: 'e4', black: 'e5' })
  })

  it('handles promotion notation', () => {
    const moves = parsePgnToMoves('1. e4 e5 2. e8=Q')
    expect(moves[1].white).toBe('e8=Q')
  })
})

// FEN used in promotion tests: white pawn on a7, black pawn on b2
const PROMO_FEN = '8/P7/8/8/8/8/1p6/4K2k w - - 0 1'

describe('isPawnPromotion', () => {
  it('returns true when white pawn moves to rank 8', () => {
    expect(isPawnPromotion(PROMO_FEN, 'a7', 'a8')).toBe(true)
  })

  it('returns true when black pawn moves to rank 1', () => {
    expect(isPawnPromotion(PROMO_FEN, 'b2', 'b1')).toBe(true)
  })

  it('returns false for a normal white pawn advance (not rank 8)', () => {
    // white pawn on e2 advancing to e4
    const fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
    expect(isPawnPromotion(fen, 'e2', 'e4')).toBe(false)
  })

  it('returns false for a non-pawn piece moving to rank 8', () => {
    // white rook on a1 — not a pawn
    const fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
    expect(isPawnPromotion(fen, 'a1', 'a8')).toBe(false)
  })

  it('returns false for empty source square', () => {
    const fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
    expect(isPawnPromotion(fen, 'e5', 'e8')).toBe(false)
  })

  it('returns true for white pawn promotion via capture', () => {
    // white pawn on g7 capturing on h8
    const fen = '7r/6P1/8/8/8/8/8/4K2k w - - 0 1'
    expect(isPawnPromotion(fen, 'g7', 'h8')).toBe(true)
  })
})
