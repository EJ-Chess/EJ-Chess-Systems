package de.eljachess.chess.api.persistence

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import slick.jdbc.H2Profile
import slick.jdbc.JdbcBackend.Database
import scala.concurrent.Await
import scala.concurrent.duration.*

/**
 * Unit tests for GameRepository using H2 in-memory.
 *
 * No Quarkus container needed — tests run against a real (H2) database
 * so they verify actual SQL behaviour, not mocks.
 */
class GameRepositorySpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach:

  private val db: Database = Database.forURL(
    "jdbc:h2:mem:chess_repo_test;DB_CLOSE_DELAY=-1",
    driver = "org.h2.Driver"
  )
  private val tables = Tables(H2Profile)

  override def beforeEach(): Unit =
    Await.result(db.run(tables.createSchemaAction), 5.seconds)

  override def afterEach(): Unit =
    Await.result(db.run(tables.dropSchemaAction), 5.seconds)

  // ── Helper: build a minimal repository wired to the test DB ────────────────
  private def makeRepo(): GameRepository =
    val cfg = new DatabaseConfig:
      override def init(): Unit = ()
      override def db      = GameRepositorySpec.this.db
      override def tables  = GameRepositorySpec.this.tables
      override def profile = H2Profile
    val repo = new GameRepository
    repo.dbConfig = cfg
    repo

  // ── Tests ──────────────────────────────────────────────────────────────────

  "GameRepository" should "insert and find a game by id" in {
    val repo = makeRepo()
    val row  = GameRow("abc-1", "", "WHITE", None, None)
    repo.insert(row)
    repo.findById("abc-1") shouldBe Some(row)
  }

  it should "return None for an unknown game id" in {
    makeRepo().findById("does-not-exist") shouldBe None
  }

  it should "update pgn correctly" in {
    val repo = makeRepo()
    repo.insert(GameRow("abc-2", "", "WHITE", None, None))
    repo.updatePgn("abc-2", "[White \"a\"]\n\n1. e4 *")
    repo.findById("abc-2").map(_.pgn) shouldBe Some("[White \"a\"]\n\n1. e4 *")
  }

  it should "delete a game" in {
    val repo = makeRepo()
    repo.insert(GameRow("abc-3", "", "WHITE", None, None))
    repo.delete("abc-3")
    repo.findById("abc-3") shouldBe None
  }

  it should "store bot config fields" in {
    val repo = makeRepo()
    val row  = GameRow("abc-4", "", "WHITE", Some("BLACK"), Some(1600))
    repo.insert(row)
    val found = repo.findById("abc-4")
    found.map(_.botColor) shouldBe Some(Some("BLACK"))
    found.map(_.botElo)   shouldBe Some(Some(1600))
  }

  it should "findAll returns all inserted games" in {
    val repo = makeRepo()
    repo.insert(GameRow("abc-5", "", "WHITE", None, None))
    repo.insert(GameRow("abc-6", "", "BLACK", Some("WHITE"), Some(1400)))
    repo.findAll().map(_.id).toSet shouldBe Set("abc-5", "abc-6")
  }
