package de.eljachess.tournament.persistence

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import scala.concurrent.Await
import scala.concurrent.duration.*

@ApplicationScoped
class TournamentRepository:
  @Inject var dbConfig: DatabaseConfig = uninitialized

  private val timeout = 5.seconds

  def tables = dbConfig.tables

  // Tournaments
  def insertTournament(row: Any): Unit =
    Await.result(dbConfig.db.run(dbConfig.tables.insertTournamentAction(row.asInstanceOf)), timeout)

  def findTournament(id: String) =
    Await.result(dbConfig.db.run(dbConfig.tables.findTournamentAction(id)), timeout)

  def allTournaments() =
    Await.result(dbConfig.db.run(dbConfig.tables.allTournamentsAction), timeout).toList

  def updateStatus(id: String, status: String): Int =
    Await.result(dbConfig.db.run(dbConfig.tables.updateStatusAction(id, status)), timeout)

  def updateRound(id: String, round: Int): Int =
    Await.result(dbConfig.db.run(dbConfig.tables.updateRoundAction(id, round)), timeout)

  def deleteTournament(id: String): Int =
    Await.result(dbConfig.db.run(dbConfig.tables.deleteTournamentAction(id)), timeout)

  // Players
  def insertPlayer(row: Any): Unit =
    Await.result(dbConfig.db.run(dbConfig.tables.insertPlayerAction(row.asInstanceOf)), timeout)

  def findPlayers(tournamentId: String) =
    Await.result(dbConfig.db.run(dbConfig.tables.findPlayersAction(tournamentId)), timeout).toList

  def findPlayer(tournamentId: String, botId: String) =
    Await.result(dbConfig.db.run(dbConfig.tables.findPlayerAction(tournamentId, botId)), timeout)

  def updatePlayerResult(
    tournamentId: String,
    botId: String,
    points: Double,
    tieBreak: Double,
    wins: Int,
    draws: Int,
    losses: Int,
    nbGames: Int,
    colorBalance: Int
  ): Int =
    Await.result(
      dbConfig.db.run(
        dbConfig.tables.updatePlayerResultAction(
          tournamentId,
          botId,
          points,
          tieBreak,
          wins,
          draws,
          losses,
          nbGames,
          colorBalance
        )
      ),
      timeout
    )

  def deletePlayer(tournamentId: String, botId: String): Int =
    Await.result(dbConfig.db.run(dbConfig.tables.deletePlayerAction(tournamentId, botId)), timeout)

  // Pairings
  def insertPairing(row: Any): Unit =
    Await.result(dbConfig.db.run(dbConfig.tables.insertPairingAction(row.asInstanceOf)), timeout)

  def findPairings(tournamentId: String) =
    Await.result(dbConfig.db.run(dbConfig.tables.findPairingsAction(tournamentId)), timeout).toList

  def findRoundPairings(tournamentId: String, round: Int) =
    Await.result(
      dbConfig.db.run(dbConfig.tables.findRoundPairingsAction(tournamentId, round)),
      timeout
    ).toList

  def updatePairingResult(id: String, gameId: String, winner: Option[String]): Int =
    Await.result(dbConfig.db.run(dbConfig.tables.updatePairingResultAction(id, gameId, winner)), timeout)
