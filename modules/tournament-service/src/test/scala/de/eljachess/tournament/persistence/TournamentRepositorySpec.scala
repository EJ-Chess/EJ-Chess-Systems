package de.eljachess.tournament.persistence

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach}
import scala.concurrent.Await
import scala.concurrent.duration.*
import slick.jdbc.H2Profile.api.*

class TournamentRepositorySpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach:
  private var db: Option[Database] = None
  private var repo: Option[TournamentRepository] = None

  override def beforeEach(): Unit =
    val testDb = Database.forURL(
      url = "jdbc:h2:mem:test_tournament_repo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      user = "sa",
      password = "",
      driver = "org.h2.Driver"
    )
    db = Some(testDb)

    val tables = new Tables(slick.jdbc.H2Profile)
    Await.result(testDb.run(tables.createSchemaAction), 5.seconds)

    val repository = new TournamentRepository()
    repository.dbConfig = new DatabaseConfig {
      override lazy val db = testDb.asInstanceOf[slick.jdbc.JdbcBackend.Database]
      override lazy val tables = new Tables(slick.jdbc.H2Profile)
      override lazy val profile = slick.jdbc.H2Profile
    }
    repo = Some(repository)

  override def afterEach(): Unit =
    db.foreach(_.close())

  "TournamentRepository" should "insert and retrieve a tournament" in {
    val repository = repo.get
    val row = repository.tables.TournamentRow(
      id = "t1",
      name = "Test Tournament",
      status = "created",
      nbRounds = 3,
      currentRound = 0,
      clockLimit = 300,
      clockIncrement = 3,
      rated = true,
      createdBy = "director1",
      startsAt = None
    )

    repository.insertTournament(row)
    val retrieved = repository.findTournament("t1")

    retrieved shouldBe defined
    retrieved.map(_.name) should be(Some("Test Tournament"))
  }

  it should "update tournament status" in {
    val repository = repo.get
    val row = repository.tables.TournamentRow(
      id = "t1",
      name = "Test",
      status = "created",
      nbRounds = 3,
      currentRound = 0,
      clockLimit = 300,
      clockIncrement = 3,
      rated = true,
      createdBy = "director1",
      startsAt = None
    )

    repository.insertTournament(row)
    repository.updateStatus("t1", "started")
    val updated = repository.findTournament("t1")

    updated.map(_.status) should be(Some("started"))
  }

  it should "insert and retrieve players" in {
    val repository = repo.get
    val tRow = repository.tables.TournamentRow(
      id = "t1",
      name = "Test",
      status = "created",
      nbRounds = 3,
      currentRound = 0,
      clockLimit = 300,
      clockIncrement = 3,
      rated = true,
      createdBy = "director1",
      startsAt = None
    )

    repository.insertTournament(tRow)

    val pRow = repository.tables.PlayerRow(
      tournamentId = "t1",
      botId = "bot1",
      botName = "Bot 1",
      points = 0.0,
      tieBreak = 0.0,
      wins = 0,
      draws = 0,
      losses = 0,
      nbGames = 0,
      colorBalance = 0
    )

    repository.insertPlayer(pRow)
    val players = repository.findPlayers("t1")

    players should have length 1
    players.head.botId should equal("bot1")
  }

  it should "insert and retrieve pairings" in {
    val repository = repo.get
    val pRow = repository.tables.PairingRow(
      id = "p1",
      tournamentId = "t1",
      round = 1,
      whiteId = "bot1",
      whiteName = "Bot 1",
      blackId = "bot2",
      blackName = "Bot 2",
      gameId = Some("g1"),
      winner = None
    )

    repository.insertPairing(pRow)
    val pairings = repository.findPairings("t1")

    pairings should have length 1
    pairings.head.whiteId should equal("bot1")
  }
