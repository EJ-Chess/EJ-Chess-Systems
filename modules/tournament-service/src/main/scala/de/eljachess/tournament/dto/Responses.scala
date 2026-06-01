package de.eljachess.tournament.dto

// Reference objects
case class BotRef(
  id: String,
  name: String
)

case class Clock(
  limit: Int,
  increment: Int
)

case class Variant(
  key: String = "standard",
  name: String = "Standard"
)

// Tournament info (lightweight, used in lists)
case class TournamentInfo(
  id: String,
  fullName: String,
  clock: Clock,
  variant: Variant,
  rated: Boolean,
  nbPlayers: Int,
  nbRounds: Int,
  createdBy: String,
  startsAt: Option[String]
)

// Standing (embedded in Tournament response)
case class Standing(
  page: Int,
  players: List[Result]
)

// Full tournament with standings
case class Tournament(
  id: String,
  fullName: String,
  clock: Clock,
  variant: Variant,
  rated: Boolean,
  nbPlayers: Int,
  nbRounds: Int,
  createdBy: String,
  startsAt: Option[String],
  status: String,  // created|started|finished
  round: Int,
  standing: Standing,
  winner: Option[BotRef] = None
)

// Player result / standing row
case class Result(
  rank: Int,
  points: Double,
  tieBreak: Double,
  bot: BotRef,
  nbGames: Int,
  wins: Int,
  draws: Int,
  losses: Int
)

// Pairing for a round
case class Pairing(
  round: Int,
  white: BotRef,
  black: BotRef,
  gameId: String,
  winner: Option[String] = None  // 'white'|'black'|'draw'|null=ongoing
)

// Game export (one line per game, NDJSON)
case class GameExport(
  id: String,
  round: Int,
  white: BotRef,
  black: BotRef,
  winner: Option[String],
  moves: String
)

// Tournament event (NDJSON streaming)
case class TournamentEvent(
  `type`: String,  // tournamentStarted|roundStarted|gameStart|roundFinished|tournamentFinished
  round: Option[Int] = None,
  gameId: Option[String] = None,
  color: Option[String] = None,  // 'white'|'black'
  winner: Option[BotRef] = None
)

// Generic responses
case class Ok(
  ok: Boolean = true
)

case class Error(
  error: String
)
