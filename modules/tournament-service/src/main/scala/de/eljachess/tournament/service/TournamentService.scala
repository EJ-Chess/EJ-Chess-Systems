package de.eljachess.tournament.service

import de.eljachess.tournament.dto.{CreateTournamentRequest, Tournament, TournamentInfo, TournamentEvent, BotRef, Clock, Variant, Standing, Result, Pairing}
import de.eljachess.tournament.persistence.{TournamentRepository, Tables}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import scala.reflect.Selectable.reflectiveSelectable
import java.util.UUID
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI

@ApplicationScoped
class TournamentService:
  @Inject var repository: TournamentRepository = uninitialized
  @Inject var swissService: SwissService = uninitialized
  @Inject var streamService: TournamentStreamService = uninitialized

  private val http = HttpClient.newHttpClient()
  private val chessApiUrl = sys.props.getOrElse("chessApiUrl", "http://localhost:8080")

  // ── Public API ──────────────────────────────────────────────────────────

  def createTournament(req: CreateTournamentRequest, directorId: String): Either[String, Tournament] =
    val id = UUID.randomUUID().toString
    val row = repository.tables.TournamentRow(
      id = id,
      name = req.name,
      status = "created",
      nbRounds = req.nbRounds,
      currentRound = 0,
      clockLimit = req.clockLimit,
      clockIncrement = req.clockIncrement,
      rated = req.rated.getOrElse(true),
      createdBy = directorId,
      startsAt = None
    )
    try
      repository.insertTournament(row)
      streamService.initTournament(id)
      Right(toTournament(row, Nil))
    catch
      case e: Exception => Left(s"400: ${e.getMessage}")

  def joinTournament(tournamentId: String, botId: String, botName: String): Either[String, Unit] =
    for
      t <- findOrError(tournamentId)
      _ <- if t.status != "created" then Left("409: tournament already started") else Right(())
      _ <- if repository.findPlayer(tournamentId, botId).isDefined then Left("409: bot already joined") else Right(())
    yield
      val row = repository.tables.PlayerRow(
        tournamentId = tournamentId,
        botId = botId,
        botName = botName,
        points = 0.0,
        tieBreak = 0.0,
        wins = 0,
        draws = 0,
        losses = 0,
        nbGames = 0,
        colorBalance = 0
      )
      repository.insertPlayer(row)

  def withdrawFromTournament(tournamentId: String, botId: String): Either[String, Unit] =
    for
      t <- findOrError(tournamentId)
      _ <- if t.status != "created" then Left("409: cannot withdraw after tournament started") else Right(())
      _ <- if repository.findPlayer(tournamentId, botId).isEmpty then Left("404: bot not in tournament") else Right(())
    yield
      repository.deletePlayer(tournamentId, botId)

  def startTournament(tournamentId: String, directorId: String): Either[String, Tournament] =
    for
      t <- findOrError(tournamentId)
      _ <- if t.status != "created" then Left("409: tournament already started or finished") else Right(())
      _ <- if t.createdBy != directorId then Left("403: only director can start tournament") else Right(())
      players <- Right(repository.findPlayers(tournamentId))
      _ <- if players.length < 2 then Left("409: need at least 2 bots to start") else Right(())
    yield
      repository.updateStatus(tournamentId, "started")
      streamService.publish(tournamentId, TournamentEvent("tournamentStarted", None, None, None, None))
      repository.updateRound(tournamentId, 1)
      startRound(tournamentId, 1)
      toTournament(repository.findTournament(tournamentId).get, players)

  def findTournament(id: String): Option[Tournament] =
    for
      row <- repository.findTournament(id)
      players <- Some(repository.findPlayers(id))
    yield toTournament(row, players)

  def listTournaments(): Map[String, List[Tournament]] =
    val all = repository.allTournaments()
    val grouped = all.groupBy(_.status)
    Map(
      "created"  -> grouped.getOrElse("created", Nil).map(t => toTournament(t, repository.findPlayers(t.id))),
      "started"  -> grouped.getOrElse("started", Nil).map(t => toTournament(t, repository.findPlayers(t.id))),
      "finished" -> grouped.getOrElse("finished", Nil).map(t => toTournament(t, repository.findPlayers(t.id)))
    )

  def getStandings(tournamentId: String): List[Result] =
    val players = repository.findPlayers(tournamentId)
    val pairings = repository.findPairings(tournamentId)
    val sorted = players.sortBy(p => (-p.points, -p.tieBreak))
    sorted.zipWithIndex.map { case (p, idx) =>
      Result(
        rank = idx + 1,
        points = p.points,
        tieBreak = p.tieBreak,
        bot = BotRef(p.botId, p.botName),
        nbGames = p.nbGames,
        wins = p.wins,
        draws = p.draws,
        losses = p.losses
      )
    }

  def getRoundPairings(tournamentId: String, round: Int): List[Pairing] =
    repository.findRoundPairings(tournamentId, round).map { p =>
      Pairing(
        round = p.round,
        white = BotRef(p.whiteId, p.whiteName),
        black = BotRef(p.blackId, p.blackName),
        gameId = p.gameId.getOrElse(""),
        winner = p.winner
      )
    }

  // ── Private helpers ─────────────────────────────────────────────────────

  private def startRound(tournamentId: String, round: Int): Unit =
    val players = repository.findPlayers(tournamentId)
    val pairings = repository.findPairings(tournamentId)
    val newPairs = swissService.computePairings(players, pairings)

    streamService.publish(tournamentId, TournamentEvent("roundStarted", Some(round), None, None, None))

    for (white, black) <- newPairs do
      val pairingId = UUID.randomUUID().toString
      val gameIdOpt = createGame(white.botId, black.botId)
      repository.insertPairing(repository.tables.PairingRow(
        id = pairingId,
        tournamentId = tournamentId,
        round = round,
        whiteId = white.botId,
        whiteName = white.botName,
        blackId = black.botId,
        blackName = black.botName,
        gameId = gameIdOpt,
        winner = None
      ))
      for gameId <- gameIdOpt do
        streamService.publish(tournamentId, TournamentEvent("gameStart", Some(round), Some(gameId), Some("white"), None))
        streamService.publish(tournamentId, TournamentEvent("gameStart", Some(round), Some(gameId), Some("black"), None))

  private def createGame(whiteId: String, blackId: String): Option[String] =
    try
      val body = """{"playerColor":"white"}"""
      val req = HttpRequest.newBuilder()
        .uri(URI.create(s"$chessApiUrl/games"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
      val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
      if resp.statusCode() == 201 then
        val json = resp.body()
        val pattern = """"gameId"\s*:\s*"([^"]+)"""".r
        pattern.findFirstMatchIn(json).map(_.group(1))
      else None
    catch
      case e: Exception =>
        println(s"Error creating game: $e")
        None

  private def findOrError(id: String) =
    repository.findTournament(id) match
      case Some(t) => Right(t)
      case None    => Left("404: tournament not found")

  private def toTournament(row: Any, players: Any): Tournament =
    val r = row.asInstanceOf[{
      def id: String
      def name: String
      def clockLimit: Int
      def clockIncrement: Int
      def rated: Boolean
      def nbRounds: Int
      def createdBy: String
      def startsAt: Option[String]
      def status: String
      def currentRound: Int
    }]
    val p = players.asInstanceOf[List[Any]]
    val standings = getStandings(r.id)
    Tournament(
      id = r.id,
      fullName = r.name,
      clock = Clock(r.clockLimit, r.clockIncrement),
      variant = Variant("standard", "Standard"),
      rated = r.rated,
      nbPlayers = p.length,
      nbRounds = r.nbRounds,
      createdBy = r.createdBy,
      startsAt = r.startsAt,
      status = r.status,
      round = r.currentRound,
      standing = Standing(1, standings),
      winner = None
    )
