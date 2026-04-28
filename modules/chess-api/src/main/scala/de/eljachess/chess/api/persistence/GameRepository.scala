package de.eljachess.chess.api.persistence

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import scala.concurrent.Await
import scala.concurrent.duration.*

/**
 * CRUD repository for the `games` table.
 *
 * Delegates all DBIO construction to `Tables` (where profile.api is in scope),
 * so this class never needs to import the Slick profile API itself —
 * avoiding Scala 3 path-dependent type issues.
 */
@ApplicationScoped
class GameRepository:

  @Inject
  var dbConfig: DatabaseConfig = uninitialized

  private val timeout = 5.seconds

  def insert(row: GameRow): Unit =
    Await.result(dbConfig.db.run(dbConfig.tables.insertAction(row)), timeout)

  def findById(id: String): Option[GameRow] =
    Await.result(dbConfig.db.run(dbConfig.tables.findByIdAction(id)), timeout)

  def updatePgn(id: String, pgn: String): Unit =
    Await.result(dbConfig.db.run(dbConfig.tables.updatePgnAction(id, pgn)), timeout)

  def delete(id: String): Unit =
    Await.result(dbConfig.db.run(dbConfig.tables.deleteAction(id)), timeout)

  def findAll(): Seq[GameRow] =
    Await.result(dbConfig.db.run(dbConfig.tables.findAllAction()), timeout)
