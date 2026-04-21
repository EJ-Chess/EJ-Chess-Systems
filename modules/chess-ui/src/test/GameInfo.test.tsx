import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { GameInfo } from '../components/GameInfo'
import type { GameStateResponse } from '../api/chessApi'

const base: GameStateResponse = {
  gameId: 'test-id',
  fen: 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
  currentTurn: 'WHITE',
  fullmoveNumber: 1,
  halfmoveClock: 0,
  inCheck: false,
  inCheckmate: false,
  inStalemate: false,
  legalMovesCount: 20,
}

describe('GameInfo', () => {
  it('shows no status badge in a normal position', () => {
    render(<GameInfo gameState={base} />)
    expect(screen.queryByText(/Patt/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Schachmatt/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Schach/i)).not.toBeInTheDocument()
  })

  it('shows "Patt — Remis" badge when inStalemate is true', () => {
    render(<GameInfo gameState={{ ...base, inStalemate: true }} />)
    expect(screen.getByText(/Patt — Remis/i)).toBeInTheDocument()
  })

  it('shows "Schachmatt" badge when inCheckmate is true (black to move → white wins)', () => {
    render(
      <GameInfo gameState={{ ...base, currentTurn: 'BLACK', inCheckmate: true }} />,
    )
    expect(screen.getByText(/Schachmatt/i)).toBeInTheDocument()
    expect(screen.getByText(/Weiß gewinnt/i)).toBeInTheDocument()
  })

  it('shows "Schachmatt" badge when inCheckmate is true (white to move → black wins)', () => {
    render(
      <GameInfo gameState={{ ...base, currentTurn: 'WHITE', inCheckmate: true }} />,
    )
    expect(screen.getByText(/Schachmatt/i)).toBeInTheDocument()
    expect(screen.getByText(/Schwarz gewinnt/i)).toBeInTheDocument()
  })

  it('shows "Schach!" badge when inCheck is true', () => {
    render(<GameInfo gameState={{ ...base, inCheck: true }} />)
    expect(screen.getByText(/Schach!/i)).toBeInTheDocument()
  })

  it('does not show "Schach!" badge when inCheckmate is true (checkmate takes priority)', () => {
    render(
      <GameInfo gameState={{ ...base, inCheck: true, inCheckmate: true }} />,
    )
    expect(screen.getByText(/Schachmatt/i)).toBeInTheDocument()
    expect(screen.queryByText(/^Schach!$/i)).not.toBeInTheDocument()
  })
})
