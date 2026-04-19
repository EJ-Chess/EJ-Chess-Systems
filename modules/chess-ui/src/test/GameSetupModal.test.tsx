import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { GameSetupModal } from '../components/GameSetupModal'

function renderModal(overrides: Partial<Parameters<typeof GameSetupModal>[0]> = {}) {
  const onStart = vi.fn()
  const onCancel = vi.fn()
  render(
    <GameSetupModal
      open={true}
      onStart={onStart}
      onCancel={onCancel}
      {...overrides}
    />,
  )
  return { onStart, onCancel }
}

describe('GameSetupModal', () => {
  it('renders opponent selection buttons', () => {
    renderModal()
    expect(screen.getByTestId('btn-opponent-human')).toBeInTheDocument()
    expect(screen.getByTestId('btn-opponent-bot')).toBeInTheDocument()
  })

  it('defaults to human opponent — no color or ELO options visible', () => {
    renderModal()
    expect(screen.queryByTestId('btn-color-white')).not.toBeInTheDocument()
    expect(screen.queryByTestId('btn-color-black')).not.toBeInTheDocument()
    expect(screen.queryByTestId('btn-elo-1400')).not.toBeInTheDocument()
  })

  it('shows color and ELO options when bot is selected', () => {
    renderModal()
    fireEvent.click(screen.getByTestId('btn-opponent-bot'))
    expect(screen.getByTestId('btn-color-white')).toBeInTheDocument()
    expect(screen.getByTestId('btn-color-black')).toBeInTheDocument()
    expect(screen.getByTestId('btn-elo-1400')).toBeInTheDocument()
  })

  it('hides bot options again when switching back to human', () => {
    renderModal()
    fireEvent.click(screen.getByTestId('btn-opponent-bot'))
    fireEvent.click(screen.getByTestId('btn-opponent-human'))
    expect(screen.queryByTestId('btn-color-white')).not.toBeInTheDocument()
    expect(screen.queryByTestId('btn-elo-1400')).not.toBeInTheDocument()
  })

  it('calls onStart with opponent=human when human is selected', () => {
    const { onStart } = renderModal()
    fireEvent.click(screen.getByTestId('btn-setup-start'))
    expect(onStart).toHaveBeenCalledWith({ opponent: 'human' })
  })

  it('calls onStart with bot options using defaults', () => {
    const { onStart } = renderModal()
    fireEvent.click(screen.getByTestId('btn-opponent-bot'))
    fireEvent.click(screen.getByTestId('btn-setup-start'))
    expect(onStart).toHaveBeenCalledWith({
      opponent: 'bot',
      playerColor: 'white',
      botElo: 1400,
    })
  })

  it('calls onStart with black color when black is selected', () => {
    const { onStart } = renderModal()
    fireEvent.click(screen.getByTestId('btn-opponent-bot'))
    fireEvent.click(screen.getByTestId('btn-color-black'))
    fireEvent.click(screen.getByTestId('btn-setup-start'))
    expect(onStart).toHaveBeenCalledWith({
      opponent: 'bot',
      playerColor: 'black',
      botElo: 1400,
    })
  })

  it('calls onStart with custom ELO when a different preset is selected', () => {
    const { onStart } = renderModal()
    fireEvent.click(screen.getByTestId('btn-opponent-bot'))
    fireEvent.click(screen.getByTestId('btn-elo-800'))
    fireEvent.click(screen.getByTestId('btn-setup-start'))
    expect(onStart).toHaveBeenCalledWith({
      opponent: 'bot',
      playerColor: 'white',
      botElo: 800,
    })
  })

  it('calls onCancel when Abbrechen is clicked', () => {
    const { onCancel } = renderModal()
    fireEvent.click(screen.getByTestId('btn-setup-cancel'))
    expect(onCancel).toHaveBeenCalled()
  })

  it('does not render when open is false', () => {
    renderModal({ open: false })
    expect(screen.queryByTestId('btn-setup-start')).not.toBeInTheDocument()
  })
})
