import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { chessApi, ApiError } from '../api/chessApi'

const INITIAL_FEN =
  'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
const GAME_ID = 'test-game-id'

const mockGameState = {
  gameId: GAME_ID,
  fen: INITIAL_FEN,
  currentTurn: 'WHITE' as const,
  fullmoveNumber: 1,
  halfmoveClock: 0,
  inCheck: false,
  inCheckmate: false,
  inStalemate: false,
  legalMovesCount: 20,
}

const mockLegalMoves = {
  gameId: GAME_ID,
  currentTurn: 'WHITE' as const,
  legalMoves: [
    { from: 'e2', to: 'e4' },
    { from: 'e2', to: 'e3' },
    { from: 'd2', to: 'd4' },
  ],
  count: 20,
}

const server = setupServer(
  http.post('/games', () =>
    HttpResponse.json(
      { gameId: GAME_ID, fen: INITIAL_FEN },
      { status: 201 },
    ),
  ),
  http.get(`/games/${GAME_ID}`, () => HttpResponse.json(mockGameState)),
  http.delete(`/games/${GAME_ID}`, () => new HttpResponse(null, { status: 204 })),
  http.get(`/games/${GAME_ID}/moves`, () => HttpResponse.json(mockLegalMoves)),
  http.post(`/games/${GAME_ID}/moves`, () =>
    HttpResponse.json({ success: true }),
  ),
  http.post(`/games/${GAME_ID}/undo`, () =>
    HttpResponse.json({ success: true, fen: INITIAL_FEN }),
  ),
  http.post(`/games/${GAME_ID}/redo`, () =>
    HttpResponse.json({ success: true, fen: INITIAL_FEN }),
  ),
  http.get(`/games/${GAME_ID}/fen`, () =>
    HttpResponse.json({ gameId: GAME_ID, fen: INITIAL_FEN }),
  ),
  http.get(`/games/${GAME_ID}/pgn`, () =>
    HttpResponse.json({ gameId: GAME_ID, pgn: '1. e4 e5' }),
  ),
  http.post(`/games/${GAME_ID}/import`, () =>
    HttpResponse.json({ success: true, fen: INITIAL_FEN, moveCount: 2 }),
  ),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('chessApi.createGame', () => {
  it('returns gameId and fen on success', async () => {
    const result = await chessApi.createGame()
    expect(result.gameId).toBe(GAME_ID)
    expect(result.fen).toBe(INITIAL_FEN)
  })

  it('throws ApiError on server error', async () => {
    server.use(
      http.post('/games', () =>
        HttpResponse.json({ error: 'Internal error' }, { status: 500 }),
      ),
    )
    await expect(chessApi.createGame()).rejects.toBeInstanceOf(ApiError)
  })
})

describe('chessApi.getGame', () => {
  it('returns full game state', async () => {
    const result = await chessApi.getGame(GAME_ID)
    expect(result.gameId).toBe(GAME_ID)
    expect(result.currentTurn).toBe('WHITE')
    expect(result.inCheck).toBe(false)
    expect(result.legalMovesCount).toBe(20)
  })

  it('throws ApiError with status 404 when game not found', async () => {
    server.use(
      http.get('/games/missing', () =>
        HttpResponse.json({ error: 'Not found' }, { status: 404 }),
      ),
    )
    const err = await chessApi.getGame('missing').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(404)
  })
})

describe('chessApi.getLegalMoves', () => {
  it('returns legal moves list', async () => {
    const result = await chessApi.getLegalMoves(GAME_ID)
    expect(result.legalMoves).toHaveLength(3)
    expect(result.legalMoves[0]).toEqual({ from: 'e2', to: 'e4' })
  })
})

describe('chessApi.makeMove', () => {
  it('resolves without error on valid move', async () => {
    // makeMove returns void — we only care that it does not throw
    await expect(
      chessApi.makeMove(GAME_ID, { from: 'e2', to: 'e4' }),
    ).resolves.not.toThrow()
  })

  it('throws ApiError on illegal move', async () => {
    server.use(
      http.post(`/games/${GAME_ID}/moves`, () =>
        HttpResponse.json({ error: 'Illegal move' }, { status: 400 }),
      ),
    )
    await expect(
      chessApi.makeMove(GAME_ID, { from: 'e2', to: 'e6' }),
    ).rejects.toBeInstanceOf(ApiError)
  })
})

describe('chessApi.undo', () => {
  it('returns success and fen', async () => {
    const result = await chessApi.undo(GAME_ID)
    expect(result.success).toBe(true)
    expect(result.fen).toBe(INITIAL_FEN)
  })
})

describe('chessApi.redo', () => {
  it('returns success and fen', async () => {
    const result = await chessApi.redo(GAME_ID)
    expect(result.success).toBe(true)
    expect(result.fen).toBe(INITIAL_FEN)
  })
})

describe('chessApi.getFen', () => {
  it('returns fen string', async () => {
    const result = await chessApi.getFen(GAME_ID)
    expect(result.fen).toBe(INITIAL_FEN)
  })
})

describe('chessApi.getPgn', () => {
  it('returns pgn string', async () => {
    const result = await chessApi.getPgn(GAME_ID)
    expect(result.pgn).toBe('1. e4 e5')
  })
})

describe('chessApi.importGame', () => {
  it('imports via FEN and returns success', async () => {
    const result = await chessApi.importGame(GAME_ID, { fen: INITIAL_FEN })
    expect(result.success).toBe(true)
    expect(result.fen).toBe(INITIAL_FEN)
  })

  it('imports via PGN and returns move count', async () => {
    const result = await chessApi.importGame(GAME_ID, { pgn: '1. e4 e5' })
    expect(result.success).toBe(true)
    expect(result.moveCount).toBe(2)
  })
})

describe('chessApi.deleteGame', () => {
  it('resolves without error', async () => {
    await expect(chessApi.deleteGame(GAME_ID)).resolves.toBeUndefined()
  })
})
