import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ImportExportModal } from '../components/ImportExportModal'

const SAMPLE_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
const SAMPLE_PGN = '1. e4 e5'

const noop = vi.fn().mockResolvedValue(undefined)

async function openModal() {
  await userEvent.click(screen.getByTestId('btn-import-export'))
}

describe('ImportExportModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the trigger button', () => {
    render(
      <ImportExportModal
        currentFen={SAMPLE_FEN}
        currentPgn={SAMPLE_PGN}
        onImportFen={noop}
        onImportPgn={noop}
      />,
    )
    expect(screen.getByTestId('btn-import-export')).toBeInTheDocument()
  })

  it('shows the current FEN in the export area', async () => {
    render(
      <ImportExportModal
        currentFen={SAMPLE_FEN}
        currentPgn=""
        onImportFen={noop}
        onImportPgn={noop}
      />,
    )
    await openModal()
    expect(screen.getByTestId('fen-export-value')).toHaveTextContent(SAMPLE_FEN)
  })

  it('shows the current PGN after switching to PGN tab', async () => {
    render(
      <ImportExportModal
        currentFen=""
        currentPgn={SAMPLE_PGN}
        onImportFen={noop}
        onImportPgn={noop}
      />,
    )
    await openModal()
    await userEvent.click(screen.getByRole('tab', { name: /pgn/i }))
    expect(screen.getByTestId('pgn-export-value')).toHaveTextContent(SAMPLE_PGN)
  })

  it('calls onImportFen with typed FEN value', async () => {
    const onImportFen = vi.fn().mockResolvedValue(undefined)
    render(
      <ImportExportModal
        currentFen=""
        currentPgn=""
        onImportFen={onImportFen}
        onImportPgn={noop}
      />,
    )
    await openModal()
    await userEvent.type(screen.getByTestId('fen-import-input'), SAMPLE_FEN)
    await userEvent.click(screen.getByTestId('btn-import-fen'))
    await waitFor(() => expect(onImportFen).toHaveBeenCalledWith(SAMPLE_FEN))
  })

  it('calls onImportPgn with typed PGN value', async () => {
    const onImportPgn = vi.fn().mockResolvedValue(undefined)
    render(
      <ImportExportModal
        currentFen=""
        currentPgn=""
        onImportFen={noop}
        onImportPgn={onImportPgn}
      />,
    )
    await openModal()
    await userEvent.click(screen.getByRole('tab', { name: /pgn/i }))
    await userEvent.type(screen.getByTestId('pgn-import-input'), SAMPLE_PGN)
    await userEvent.click(screen.getByTestId('btn-import-pgn'))
    await waitFor(() => expect(onImportPgn).toHaveBeenCalledWith(SAMPLE_PGN))
  })

  it('shows error message when import fails', async () => {
    const onImportFen = vi
      .fn()
      .mockRejectedValue(new Error('Ungültige FEN-Notation'))
    render(
      <ImportExportModal
        currentFen=""
        currentPgn=""
        onImportFen={onImportFen}
        onImportPgn={noop}
      />,
    )
    await openModal()
    await userEvent.type(screen.getByTestId('fen-import-input'), 'bad-fen')
    await userEvent.click(screen.getByTestId('btn-import-fen'))
    await waitFor(() =>
      expect(screen.getByTestId('import-error')).toHaveTextContent(
        'Ungültige FEN-Notation',
      ),
    )
  })

  it('disables import button when input is empty', async () => {
    render(
      <ImportExportModal
        currentFen=""
        currentPgn=""
        onImportFen={noop}
        onImportPgn={noop}
      />,
    )
    await openModal()
    expect(screen.getByTestId('btn-import-fen')).toBeDisabled()
  })
})
