import { describe, it, expect } from 'vitest'
import { parsePgnToMoves, cn } from '../lib/utils'

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
