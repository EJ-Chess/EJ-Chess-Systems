import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GameControls } from '../components/GameControls'

const noop = vi.fn().mockResolvedValue(undefined)

describe('GameControls', () => {
  it('renders all three buttons', () => {
    render(
      <GameControls
        hasGame={true}
        loading={false}
        onNewGame={noop}
        onUndo={noop}
        onRedo={noop}
      />,
    )
    expect(screen.getByTestId('btn-new-game')).toBeInTheDocument()
    expect(screen.getByTestId('btn-undo')).toBeInTheDocument()
    expect(screen.getByTestId('btn-redo')).toBeInTheDocument()
  })

  it('hides undo and redo when no game is active', () => {
    render(
      <GameControls
        hasGame={false}
        loading={false}
        onNewGame={noop}
        onUndo={noop}
        onRedo={noop}
      />,
    )
    expect(screen.queryByTestId('btn-undo')).not.toBeInTheDocument()
    expect(screen.queryByTestId('btn-redo')).not.toBeInTheDocument()
  })

  it('disables all buttons while loading', () => {
    render(
      <GameControls
        hasGame={true}
        loading={true}
        onNewGame={noop}
        onUndo={noop}
        onRedo={noop}
      />,
    )
    expect(screen.getByTestId('btn-new-game')).toBeDisabled()
    expect(screen.getByTestId('btn-undo')).toBeDisabled()
    expect(screen.getByTestId('btn-redo')).toBeDisabled()
  })

  it('calls onNewGame when "Neues Spiel" is clicked', async () => {
    const onNewGame = vi.fn()
    render(
      <GameControls
        hasGame={false}
        loading={false}
        onNewGame={onNewGame}
        onUndo={noop}
        onRedo={noop}
      />,
    )
    await userEvent.click(screen.getByTestId('btn-new-game'))
    expect(onNewGame).toHaveBeenCalledOnce()
  })

  it('calls onUndo when Undo is clicked', async () => {
    const onUndo = vi.fn()
    render(
      <GameControls
        hasGame={true}
        loading={false}
        onNewGame={noop}
        onUndo={onUndo}
        onRedo={noop}
      />,
    )
    await userEvent.click(screen.getByTestId('btn-undo'))
    expect(onUndo).toHaveBeenCalledOnce()
  })

  it('calls onRedo when Redo is clicked', async () => {
    const onRedo = vi.fn()
    render(
      <GameControls
        hasGame={true}
        loading={false}
        onNewGame={noop}
        onUndo={noop}
        onRedo={onRedo}
      />,
    )
    await userEvent.click(screen.getByTestId('btn-redo'))
    expect(onRedo).toHaveBeenCalledOnce()
  })
})
