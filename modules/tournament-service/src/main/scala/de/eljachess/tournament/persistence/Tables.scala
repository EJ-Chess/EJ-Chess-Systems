package de.eljachess.tournament.persistence

import slick.jdbc.JdbcProfile

class Tables(val profile: JdbcProfile):
  import profile.api.*

  // Row case classes
  case class TournamentRow(
    id: String,
    name: String,
    status: String,
    nbRounds: Int,
    currentRound: Int,
    clockLimit: Int,
    clockIncrement: Int,
    rated: Boolean,
    createdBy: String,
    startsAt: Option[String]
  )

  case class PlayerRow(
    tournamentId: String,
    botId: String,
    botName: String,
    points: Double,
    tieBreak: Double,
    wins: Int,
    draws: Int,
    losses: Int,
    nbGames: Int,
    colorBalance: Int
  )

  case class PairingRow(
    id: String,
    tournamentId: String,
    round: Int,
    whiteId: String,
    whiteName: String,
    blackId: String,
    blackName: String,
    gameId: Option[String],
    winner: Option[String]
  )

  // Table definitions
  class Tournaments(tag: Tag) extends Table[TournamentRow](tag, "tournaments"):
    def id              = column[String]("id", O.PrimaryKey)
    def name            = column[String]("name")
    def status          = column[String]("status")
    def nbRounds        = column[Int]("nb_rounds")
    def currentRound    = column[Int]("current_round")
    def clockLimit      = column[Int]("clock_limit")
    def clockIncrement  = column[Int]("clock_increment")
    def rated           = column[Boolean]("rated")
    def createdBy       = column[String]("created_by")
    def startsAt        = column[Option[String]]("starts_at")

    def * = (id, name, status, nbRounds, currentRound, clockLimit, clockIncrement, rated, createdBy, startsAt).mapTo[TournamentRow]

  class Players(tag: Tag) extends Table[PlayerRow](tag, "tournament_players"):
    def tournamentId  = column[String]("tournament_id")
    def botId         = column[String]("bot_id")
    def botName       = column[String]("bot_name")
    def points        = column[Double]("points")
    def tieBreak      = column[Double]("tie_break")
    def wins          = column[Int]("wins")
    def draws         = column[Int]("draws")
    def losses        = column[Int]("losses")
    def nbGames       = column[Int]("nb_games")
    def colorBalance  = column[Int]("color_balance")

    def pk = primaryKey("pk_tournament_players", (tournamentId, botId))

    def * = (tournamentId, botId, botName, points, tieBreak, wins, draws, losses, nbGames, colorBalance).mapTo[PlayerRow]

  class Pairings(tag: Tag) extends Table[PairingRow](tag, "pairings"):
    def id            = column[String]("id", O.PrimaryKey)
    def tournamentId  = column[String]("tournament_id")
    def round         = column[Int]("round")
    def whiteId       = column[String]("white_id")
    def whiteName     = column[String]("white_name")
    def blackId       = column[String]("black_id")
    def blackName     = column[String]("black_name")
    def gameId        = column[Option[String]]("game_id")
    def winner        = column[Option[String]]("winner")

    def * = (id, tournamentId, round, whiteId, whiteName, blackId, blackName, gameId, winner).mapTo[PairingRow]

  // TableQuery instances
  val tournaments = TableQuery[Tournaments]
  val players     = TableQuery[Players]
  val pairings    = TableQuery[Pairings]

  // DBIO Actions — Tournaments
  def insertTournamentAction(row: TournamentRow) = tournaments += row

  def findTournamentAction(id: String) = tournaments.filter(_.id === id).result.headOption

  def allTournamentsAction = tournaments.result

  def updateStatusAction(id: String, status: String) =
    tournaments.filter(_.id === id).map(_.status).update(status)

  def updateRoundAction(id: String, round: Int) =
    tournaments.filter(_.id === id).map(_.currentRound).update(round)

  def deleteTournamentAction(id: String) = tournaments.filter(_.id === id).delete

  // DBIO Actions — Players
  def insertPlayerAction(row: PlayerRow) = players += row

  def findPlayersAction(tournamentId: String) =
    players.filter(_.tournamentId === tournamentId).result

  def findPlayerAction(tournamentId: String, botId: String) =
    players.filter(p => p.tournamentId === tournamentId && p.botId === botId).result.headOption

  def updatePlayerResultAction(
    tournamentId: String,
    botId: String,
    pts: Double,
    tb: Double,
    w: Int,
    d: Int,
    l: Int,
    ng: Int,
    cb: Int
  ) =
    players
      .filter(p => p.tournamentId === tournamentId && p.botId === botId)
      .map(p => (p.points, p.tieBreak, p.wins, p.draws, p.losses, p.nbGames, p.colorBalance))
      .update((pts, tb, w, d, l, ng, cb))

  def deletePlayerAction(tournamentId: String, botId: String) =
    players.filter(p => p.tournamentId === tournamentId && p.botId === botId).delete

  // DBIO Actions — Pairings
  def insertPairingAction(row: PairingRow) = pairings += row

  def findPairingsAction(tournamentId: String) =
    pairings.filter(_.tournamentId === tournamentId).result

  def findRoundPairingsAction(tournamentId: String, round: Int) =
    pairings.filter(p => p.tournamentId === tournamentId && p.round === round).result

  def updatePairingResultAction(id: String, gameId: String, winner: Option[String]) =
    pairings.filter(_.id === id).map(p => (p.gameId, p.winner)).update((Some(gameId), winner))

  // Schema
  def createSchemaAction =
    (tournaments.schema ++ players.schema ++ pairings.schema).createIfNotExists
