const BASE_URL = '/games'
const ANALYTICS_URL = '/analytics'

// ─── Response Types ───────────────────────────────────────────────────────────

export interface GameCreatedResponse {
  gameId: string
  fen: string
}

export interface GameStateResponse {
  gameId: string
  fen: string
  currentTurn: 'WHITE' | 'BLACK'
  fullmoveNumber: number
  halfmoveClock: number
  inCheck: boolean
  inCheckmate: boolean
  inStalemate: boolean
  legalMovesCount: number
}

export interface MoveNotation {
  from: string
  to: string
  promotion?: string
}

export interface LegalMovesResponse {
  gameId: string
  currentTurn: 'WHITE' | 'BLACK'
  legalMoves: MoveNotation[]
  count: number
}

export interface MakeMoveRequest {
  from?: string
  to?: string
  promotion?: string
  san?: string
}

export interface UndoRedoResponse {
  success: boolean
  fen: string
}

export interface FenResponse {
  gameId: string
  fen: string
}

export interface PgnResponse {
  gameId: string
  pgn: string
}

export interface ImportResponse {
  success: boolean
  fen?: string
  moveCount?: number
  error?: string
}

// ─── Analytics Types ──────────────────────────────────────────────────────────

export type AnalyticsStatus = 'IDLE' | 'RUNNING' | 'DONE' | 'ERROR'

export interface PlayerVictory {
  player: string
  victories: number
}

export interface ColorWin {
  color: string
  totalWins: number
}

export interface PlayerElo {
  player: string
  avgEloBeat: number
}

export interface AnalyticsResult {
  status: AnalyticsStatus
  runAt: string | null
  victoriesPerPlayer: PlayerVictory[]
  winsPerColor: ColorWin[]
  avgEloBeatPerPlayer: PlayerElo[]
  bestPlayer: string
  dataSource: 'local' | 'lichess' | ''
}

export type AnalyticsSource = 'local' | 'lichess'

// ─── Error handling ───────────────────────────────────────────────────────────

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `HTTP ${res.status}`
    try {
      const body = await res.json()
      message = body.error ?? body.message ?? message
    } catch {
      // ignore parse error
    }
    throw new ApiError(message, res.status)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

const json = (body: unknown) => JSON.stringify(body)
const jsonHeaders = { 'Content-Type': 'application/json' }

// ─── Request Types ────────────────────────────────────────────────────────────

export interface CreateGameRequest {
  playerName?: string
  playerColor?: 'white' | 'black'
  opponent?: 'human' | 'bot'
  botElo?: number
}

// ─── API Client ───────────────────────────────────────────────────────────────

export const chessApi = {
  createGame(options: CreateGameRequest = {}): Promise<GameCreatedResponse> {
    return fetch(BASE_URL, {
      method: 'POST',
      headers: jsonHeaders,
      body: json(options),
    }).then(handleResponse<GameCreatedResponse>)
  },

  getGame(id: string): Promise<GameStateResponse> {
    return fetch(`${BASE_URL}/${id}`).then(handleResponse<GameStateResponse>)
  },

  deleteGame(id: string): Promise<void> {
    return fetch(`${BASE_URL}/${id}`, { method: 'DELETE' }).then(
      handleResponse<void>,
    )
  },

  getLegalMoves(id: string): Promise<LegalMovesResponse> {
    return fetch(`${BASE_URL}/${id}/moves`).then(
      handleResponse<LegalMovesResponse>,
    )
  },

  makeMove(id: string, move: MakeMoveRequest): Promise<void> {
    return fetch(`${BASE_URL}/${id}/moves`, {
      method: 'POST',
      headers: jsonHeaders,
      body: json(move),
    }).then(handleResponse<void>)
  },

  undo(id: string): Promise<UndoRedoResponse> {
    return fetch(`${BASE_URL}/${id}/undo`, {
      method: 'POST',
      headers: jsonHeaders,
      body: json({}),
    }).then(handleResponse<UndoRedoResponse>)
  },

  redo(id: string): Promise<UndoRedoResponse> {
    return fetch(`${BASE_URL}/${id}/redo`, {
      method: 'POST',
      headers: jsonHeaders,
      body: json({}),
    }).then(handleResponse<UndoRedoResponse>)
  },

  getFen(id: string): Promise<FenResponse> {
    return fetch(`${BASE_URL}/${id}/fen`).then(handleResponse<FenResponse>)
  },

  getPgn(id: string): Promise<PgnResponse> {
    return fetch(`${BASE_URL}/${id}/pgn`).then(handleResponse<PgnResponse>)
  },

  importGame(
    id: string,
    data: { pgn?: string; fen?: string },
  ): Promise<ImportResponse> {
    return fetch(`${BASE_URL}/${id}/import`, {
      method: 'POST',
      headers: jsonHeaders,
      body: json(data),
    }).then(handleResponse<ImportResponse>)
  },
}

// ─── Analytics API ────────────────────────────────────────────────────────────

export const analyticsApi = {
  run(source: AnalyticsSource = 'local'): Promise<void> {
    return fetch(`${ANALYTICS_URL}/run?source=${source}`, {
      method: 'POST',
      headers: jsonHeaders,
    }).then(handleResponse<void>)
  },

  getStatus(): Promise<{ status: AnalyticsStatus }> {
    return fetch(`${ANALYTICS_URL}/status`).then(
      handleResponse<{ status: AnalyticsStatus }>,
    )
  },

  getResults(): Promise<AnalyticsResult> {
    return fetch(`${ANALYTICS_URL}/results`).then(
      handleResponse<AnalyticsResult>,
    )
  },
}
