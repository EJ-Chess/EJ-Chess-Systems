package de.eljachess.chess.api.persistence

import slick.jdbc.JdbcProfile

/** Top-level case class — not path-dependent, usable anywhere. */
case class GameRow(
  id:          String,
  pgn:         String,
  playerColor: String,
  botColor:    Option[String],
  botElo:      Option[Int]
)

/**
 * Slick table definitions + DBIO actions.
 *
 * All query actions are defined here where `profile.api.*` is in scope,
 * so callers (GameRepository) never need to import the profile API themselves.
 * This avoids Scala 3 path-dependent-type issues with dynamic dispatch.
 */
class Tables(val profile: JdbcProfile):
  import profile.api.*

  class GamesTable(tag: Tag) extends Table[GameRow](tag, "games"):
    def id          = column[String]("id", O.PrimaryKey)
    def pgn         = column[String]("pgn")
    def playerColor = column[String]("player_color")
    def botColor    = column[Option[String]]("bot_color")
    def botElo      = column[Option[Int]]("bot_elo")
    def *           = (id, pgn, playerColor, botColor, botElo).mapTo[GameRow]

  val games = TableQuery[GamesTable]

  // ── DBIO actions (profile.api in scope here) ──────────────────────────────

  def createSchemaAction = games.schema.createIfNotExists
  def dropSchemaAction   = games.schema.dropIfExists
  def insertAction(row: GameRow)                = games += row
  def findByIdAction(id: String)                = games.filter(_.id === id).result.headOption
  def updatePgnAction(id: String, pgn: String)  = games.filter(_.id === id).map(_.pgn).update(pgn)
  def deleteAction(id: String)                  = games.filter(_.id === id).delete
  def findAllAction()                           = games.result
