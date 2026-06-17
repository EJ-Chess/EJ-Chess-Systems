package de.eljachess.connector.service

import de.eljachess.connector.client.{BotHttpClient, TournamentHttpClient}
import de.eljachess.connector.dto.{GameEndInfo, GameStateInfo, GameStreamEvent, MoveInfo}

/** Handles one game in the tournament.
 *
 *  Opens the game event stream and plays moves whenever it is our colour's turn.
 *  Runs synchronously (blocks the calling thread) until the game ends.
 *
 *  @param tournamentId  external tournament ID
 *  @param gameId        external game ID
 *  @param myColor       "white" or "black" — the colour we were assigned
 *  @param token         bot JWT token for authentication
 *  @param serverUrl     base URL of the external tournament server
 *  @param botServiceUrl base URL of our bot-service
 *  @param elo           ELO setting forwarded to bot-service for depth selection
 *  @param httpClient    injectable for testing
 *  @param botClient     injectable for testing
 */
class GameHandler(
  tournamentId: String,
  gameId: String,
  myColor: String,
  token: String,
  serverUrl: String,
  botServiceUrl: String,
  elo: Int,
  httpClient: TournamentHttpClient,
  botClient: BotHttpClient
):
  def run(): Unit =
    println(s"[GameHandler] Starting game $gameId as $myColor in tournament $tournamentId")
    httpClient.streamGameEvents(serverUrl, tournamentId, gameId, token, handleEvent)
    println(s"[GameHandler] Game $gameId finished")

  private[service] def handleEvent(event: GameStreamEvent): Unit =
    event.gameState.foreach(handleGameState)
    event.move.foreach(handleMove)
    event.gameEnd.foreach(handleGameEnd)

  private def handleGameState(state: GameStateInfo): Unit =
    if isOngoing(state.status) && isMyTurn(state.turn) then
      playMove(state.fen)

  private def handleMove(move: MoveInfo): Unit =
    if isMyTurn(move.turn) then
      playMove(move.fen)

  private def handleGameEnd(end: GameEndInfo): Unit =
    val result = end.winner match
      case Some(w) if w == myColor => "won"
      case Some(_)                 => "lost"
      case None                    => "drew"
    println(s"[GameHandler] Game $gameId ended — we $result (status: ${end.status})")

  private[service] def isMyTurn(turn: String): Boolean = turn == myColor

  private[service] def isOngoing(status: String): Boolean =
    status == "ongoing" || status == "pending"

  private def playMove(fen: String): Unit =
    botClient.getBotMove(botServiceUrl, fen, myColor, elo) match
      case Some(uci) =>
        println(s"[GameHandler] Submitting move $uci in game $gameId")
        httpClient.submitMove(serverUrl, tournamentId, gameId, uci, token) match
          case Right(_)  => ()
          case Left(err) => println(s"[GameHandler] Move submission failed: $err")
      case None =>
        println(s"[GameHandler] Bot-service returned no move for game $gameId (fen: $fen)")
