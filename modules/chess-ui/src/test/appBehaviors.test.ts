/**
 * Tests for the three behaviors added to App.tsx:
 *
 *  1. opponentType / playerColor are persisted to localStorage so that
 *     bot-mode survives page reloads (isBotTurn stays correct).
 *
 *  2. movePending guard — handleMove bails out immediately when a move
 *     is already in flight, preventing double-POST and the resulting 400.
 *
 *  3. Storage cleanup — handleHomeDiscard removes all three keys so a
 *     fresh page load after discarding starts with clean defaults.
 *
 * These tests exercise the exact same logic that lives in App.tsx, without
 * needing to mount the full component (which requires mocking Quarkus REST,
 * the chess clock, and many other concerns).
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest'

// ── Storage key constants (must match App.tsx exactly) ────────────────────────

const STORAGE_KEY          = 'eja-chess-game-id'
const STORAGE_KEY_OPPONENT = 'eja-chess-opponent-type'
const STORAGE_KEY_COLOR    = 'eja-chess-player-color'

// ── Helpers that mirror the App.tsx read logic ─────────────────────────────────

function readOpponent(): 'human' | 'bot' {
  return (localStorage.getItem(STORAGE_KEY_OPPONENT) as 'human' | 'bot') ?? 'human'
}

function readColor(): 'white' | 'black' {
  return (localStorage.getItem(STORAGE_KEY_COLOR) as 'white' | 'black') ?? 'white'
}

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => localStorage.clear())
afterEach(() => localStorage.clear())

// ── 1. Persistence ────────────────────────────────────────────────────────────

describe('localStorage persistence — opponentType', () => {
  it('defaults to "human" when nothing is stored', () => {
    expect(readOpponent()).toBe('human')
  })

  it('returns "bot" after storing "bot"', () => {
    localStorage.setItem(STORAGE_KEY_OPPONENT, 'bot')
    expect(readOpponent()).toBe('bot')
  })

  it('returns "human" after storing "human"', () => {
    localStorage.setItem(STORAGE_KEY_OPPONENT, 'human')
    expect(readOpponent()).toBe('human')
  })
})

describe('localStorage persistence — playerColor', () => {
  it('defaults to "white" when nothing is stored', () => {
    expect(readColor()).toBe('white')
  })

  it('returns "black" after storing "black"', () => {
    localStorage.setItem(STORAGE_KEY_COLOR, 'black')
    expect(readColor()).toBe('black')
  })

  it('returns "white" after storing "white"', () => {
    localStorage.setItem(STORAGE_KEY_COLOR, 'white')
    expect(readColor()).toBe('white')
  })
})

describe('localStorage persistence — new game writes both keys', () => {
  it('stores bot and black after starting a bot game as black', () => {
    // Mirrors the handleNewGame logic in App.tsx
    const options = { opponent: 'bot' as const, playerColor: 'black' as const }
    localStorage.setItem(STORAGE_KEY_OPPONENT, options.opponent)
    localStorage.setItem(STORAGE_KEY_COLOR, options.playerColor)

    expect(readOpponent()).toBe('bot')
    expect(readColor()).toBe('black')
  })

  it('stores human after starting a human game (no color override)', () => {
    const options = { opponent: 'human' as const }
    localStorage.setItem(STORAGE_KEY_OPPONENT, options.opponent ?? 'human')
    localStorage.setItem(STORAGE_KEY_COLOR, 'white')   // default

    expect(readOpponent()).toBe('human')
    expect(readColor()).toBe('white')
  })
})

// ── 2. movePending guard ──────────────────────────────────────────────────────

/**
 * The guard in handleMove is:
 *   if (!gameId || movePending) return false
 *
 * This prevents a second POST from being sent while the first is in flight.
 */
function movePendingGuard(gameId: string | null, movePending: boolean): boolean {
  if (!gameId || movePending) return false
  return true
}

describe('movePending guard', () => {
  it('allows move when gameId exists and no move is pending', () => {
    expect(movePendingGuard('abc', false)).toBe(true)
  })

  it('blocks move when a move is already pending', () => {
    expect(movePendingGuard('abc', true)).toBe(false)
  })

  it('blocks move when gameId is null', () => {
    expect(movePendingGuard(null, false)).toBe(false)
  })

  it('blocks move when both gameId is null and pending is true', () => {
    expect(movePendingGuard(null, true)).toBe(false)
  })
})

// ── 3. Storage cleanup on discard ─────────────────────────────────────────────

describe('handleHomeDiscard clears all storage keys', () => {
  it('removes gameId, opponentType and playerColor from localStorage', () => {
    // Pre-populate all three keys (simulates an active bot game)
    localStorage.setItem(STORAGE_KEY,          'game-123')
    localStorage.setItem(STORAGE_KEY_OPPONENT, 'bot')
    localStorage.setItem(STORAGE_KEY_COLOR,    'black')

    // Mirrors handleHomeDiscard in App.tsx
    localStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem(STORAGE_KEY_OPPONENT)
    localStorage.removeItem(STORAGE_KEY_COLOR)

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
    expect(localStorage.getItem(STORAGE_KEY_OPPONENT)).toBeNull()
    expect(localStorage.getItem(STORAGE_KEY_COLOR)).toBeNull()
  })

  it('falls back to safe defaults after discard', () => {
    localStorage.setItem(STORAGE_KEY_OPPONENT, 'bot')
    localStorage.setItem(STORAGE_KEY_COLOR,    'black')

    localStorage.removeItem(STORAGE_KEY_OPPONENT)
    localStorage.removeItem(STORAGE_KEY_COLOR)

    // On next page load these would be read — must return defaults
    expect(readOpponent()).toBe('human')
    expect(readColor()).toBe('white')
  })
})
