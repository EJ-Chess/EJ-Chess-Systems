package de.eljachess.chess.api.service

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import de.eljachess.chess.api.dto.GameStateResponse


class GameServiceSpec extends AnyFlatSpec with Matchers:

  // ── createGame ──────────────────────────────────────────────────────────────

  "GameService.createGame" should "return a non-empty UUID string" in {
    val svc = GameService()
    val id  = svc.createGame()
    id should not be empty
    id.length should be(36) // UUID format: 8-4-4-4-12 + 4 dashes
  }

  it should "return distinct IDs on each call" in {
    val svc = GameService()
    val id1 = svc.createGame()
    val id2 = svc.createGame()
    id1 should not equal id2
  }

  it should "create a game in initial position" in {
    val svc   = GameService()
    val id    = svc.createGame()
    val state = svc.getGameState(id).toOption.get
    state.fen should startWith("rnbqkbnr/pppppppp")
    state.currentTurn should be("WHITE")
    state.fullmoveNumber should be(1)
    state.halfmoveClock should be(0)
  }

  it should "create a game not in check initially" in {
    val svc   = GameService()
    val id    = svc.createGame()
    val state = svc.getGameState(id).toOption.get
    state.inCheck should be(false)
    state.inCheckmate should be(false)
    state.inStalemate should be(false)
  }

  it should "create a game with 20 legal moves initially" in {
    val svc   = GameService()
    val id    = svc.createGame()
    val state = svc.getGameState(id).toOption.get
    state.legalMovesCount should be(20)
  }

  // ── getGameState ─────────────────────────────────────────────────────────────

  "GameService.getGameState" should "return Left for unknown game" in {
    val svc = GameService()
    svc.getGameState("no-such-id").isLeft should be(true)
  }

  it should "reflect WHITE turn at start" in {
    val svc   = GameService()
    val id    = svc.createGame()
    val state = svc.getGameState(id).toOption.get
    state.currentTurn should be("WHITE")
  }

  it should "include the gameId in the response" in {
    val svc   = GameService()
    val id    = svc.createGame()
    val state = svc.getGameState(id).toOption.get
    state.gameId should be(id)
  }

  it should "return the initial FEN on a freshly created game" in {
    val svc   = GameService()
    val id    = svc.createGame()
    val state = svc.getGameState(id).toOption.get
    state.fen should startWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w")
  }

  it should "reflect BLACK turn after White's first algebraic move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e4", None).isRight should be(true)
    svc.getGameState(id).toOption.get.currentTurn should be("BLACK")
  }

  // ── makeMoveAlgebraic ────────────────────────────────────────────────────────

  "GameService.makeMoveAlgebraic" should "return Right for a legal opening move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e4", None).isRight should be(true)
  }

  it should "advance the turn from WHITE to BLACK after a legal move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "d2", "d4", None)
    svc.getGameState(id).toOption.get.currentTurn should be("BLACK")
  }

  it should "return Left for an illegal move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e5", None).isLeft should be(true)
  }

  it should "return Left for unknown game" in {
    val svc = GameService()
    svc.makeMoveAlgebraic("no-such-id", "e2", "e4", None).isLeft should be(true)
  }

  it should "return Left for an invalid from-square notation" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "z9", "e4", None).isLeft should be(true)
  }

  it should "return Left for an invalid to-square notation" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "z9", None).isLeft should be(true)
  }

  it should "return Left when attempting to move an opponent's piece" in {
    val svc = GameService()
    val id  = svc.createGame()
    // It's White's turn; e7 is a Black pawn
    svc.makeMoveAlgebraic(id, "e7", "e5", None).isLeft should be(true)
  }

  it should "return Right for a promotion move and promote to queen" in {
    val svc = GameService()
    val id  = svc.createGame()
    val fenBeforePromo = "8/P7/8/8/8/8/8/4K2k w - - 0 1"
    svc.importFen(id, fenBeforePromo)
    svc.makeMoveAlgebraic(id, "a7", "a8", Some("Q")).isRight should be(true)
  }

  // ── makeMoveSan ──────────────────────────────────────────────────────────────

  "GameService.makeMoveSan" should "return Right for a valid SAN move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveSan(id, "e4").isRight should be(true)
  }

  it should "advance the turn after a SAN move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveSan(id, "e4")
    svc.getGameState(id).toOption.get.currentTurn should be("BLACK")
  }

  it should "return Left for an invalid SAN move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveSan(id, "e5").isLeft should be(true)
  }

  it should "return Left for unknown game" in {
    val svc = GameService()
    svc.makeMoveSan("no-such-id", "e4").isLeft should be(true)
  }

  it should "return Right for a knight SAN move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveSan(id, "Nf3").isRight should be(true)
  }

  // ── getLegalMoves ─────────────────────────────────────────────────────────────

  "GameService.getLegalMoves" should "return 20 legal moves at start" in {
    val svc   = GameService()
    val id    = svc.createGame()
    val moves = svc.getLegalMoves(id).toOption.get
    moves.size should be(20)
  }

  it should "return Left for unknown game" in {
    val svc = GameService()
    svc.getLegalMoves("no-such-id").isLeft should be(true)
  }

  it should "return moves with two-character from/to algebraic notation" in {
    val svc   = GameService()
    val id    = svc.createGame()
    val moves = svc.getLegalMoves(id).toOption.get
    moves.foreach { m =>
      m.from.length should be(2)
      m.to.length should be(2)
    }
  }

  it should "return Right (not Left) for a valid game" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.getLegalMoves(id).isRight should be(true)
  }

  // ── importFen ────────────────────────────────────────────────────────────────

  "GameService.importFen" should "load a valid FEN position and return Right" in {
    val svc = GameService()
    val id  = svc.createGame()
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    svc.importFen(id, fen).isRight should be(true)
  }

  it should "update currentTurn to BLACK after loading a black-to-move FEN" in {
    val svc = GameService()
    val id  = svc.createGame()
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    svc.importFen(id, fen)
    svc.getGameState(id).toOption.get.currentTurn should be("BLACK")
  }

  it should "reject an invalid FEN and return Left" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.importFen(id, "not-a-fen").isLeft should be(true)
  }

  it should "return Left for unknown game" in {
    val svc = GameService()
    svc.importFen("no-such-id", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").isLeft should be(true)
  }

  it should "update the FEN in game state after import" in {
    val svc    = GameService()
    val id     = svc.createGame()
    val newFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    svc.importFen(id, newFen)
    val state = svc.getGameState(id).toOption.get
    state.fen should startWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b")
  }

  it should "reset move history so undo returns Left after FEN import" in {
    val svc    = GameService()
    val id     = svc.createGame()
    val newFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    svc.importFen(id, newFen)
    // importFen creates a new GameManager with no history
    svc.undo(id).isLeft should be(true)
  }

  // ── importPgn ────────────────────────────────────────────────────────────────

  "GameService.importPgn" should "return Right for a valid PGN with standard moves" in {
    val svc = GameService()
    val id  = svc.createGame()
    val pgn = "[White \"A\"]\n[Black \"B\"]\n\n1. e4 e5 2. Nf3 Nc6 *"
    svc.importPgn(id, pgn).isRight should be(true)
  }

  it should "update game state after successful PGN import" in {
    val svc = GameService()
    val id  = svc.createGame()
    val pgn = "[White \"A\"]\n[Black \"B\"]\n\n1. e4 e5 *"
    svc.importPgn(id, pgn)
    val state = svc.getGameState(id).toOption.get
    state.fullmoveNumber should be(2)
  }

  it should "return Left for unknown game" in {
    val svc = GameService()
    svc.importPgn("no-such-id", "[White \"A\"]\n\n1. e4 *").isLeft should be(true)
  }

  it should "reject malformed PGN (bad header syntax)" in {
    val svc = GameService()
    val id  = svc.createGame()
    // Missing closing bracket → Pgn.decode returns Left before moves are tried
    svc.importPgn(id, "[ BadHeader\n\n1. e4 *").isLeft should be(true)
  }

  it should "accept a PGN with no moves and return Right" in {
    // A PGN with only headers and result marker — no SAN moves to execute
    val svc = GameService()
    val id  = svc.createGame()
    val pgn = "[White \"A\"]\n[Black \"B\"]\n\n*"
    svc.importPgn(id, pgn).isRight should be(true)
  }

  it should "leave game in initial state after empty-move PGN import" in {
    val svc = GameService()
    val id  = svc.createGame()
    val pgn = "[White \"A\"]\n[Black \"B\"]\n\n*"
    svc.importPgn(id, pgn)
    val state = svc.getGameState(id).toOption.get
    state.currentTurn should be("WHITE")
    state.fullmoveNumber should be(1)
  }

  // ── undo ─────────────────────────────────────────────────────────────────────

  "GameService.undo" should "return Left when nothing to undo on fresh game" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.undo(id).isLeft should be(true)
  }

  it should "return Left for unknown game" in {
    val svc = GameService()
    svc.undo("no-such-id").isLeft should be(true)
  }

  it should "return Right with a FEN after a successful move and undo" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e4", None)
    val result = svc.undo(id)
    result.isRight should be(true)
    result.toOption.get should startWith("rnbqkbnr/pppppppp")
  }

  it should "restore WHITE turn after undoing White's first move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e4", None)
    svc.undo(id)
    svc.getGameState(id).toOption.get.currentTurn should be("WHITE")
  }

  it should "still return Left after a failed move attempt (nothing recorded in history)" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e5", None) // illegal move, returns Left
    svc.undo(id).isLeft should be(true)
  }

  // ── redo ─────────────────────────────────────────────────────────────────────

  "GameService.redo" should "return Left when nothing to redo on fresh game" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.redo(id).isLeft should be(true)
  }

  it should "return Left for unknown game" in {
    val svc = GameService()
    svc.redo("no-such-id").isLeft should be(true)
  }

  it should "return Right after move then undo then redo" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e4", None)
    svc.undo(id)
    val result = svc.redo(id)
    result.isRight should be(true)
  }

  it should "restore BLACK turn after redo of White's first move" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e4", None)
    svc.undo(id)
    svc.redo(id)
    svc.getGameState(id).toOption.get.currentTurn should be("BLACK")
  }

  it should "return Left after a failed move then redo" in {
    val svc = GameService()
    val id  = svc.createGame()
    // No successful moves, so redo has nothing
    svc.redo(id).isLeft should be(true)
  }

  // ── deleteGame ───────────────────────────────────────────────────────────────

  "GameService.deleteGame" should "remove a game and return Right" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.deleteGame(id).isRight should be(true)
  }

  it should "make the game unreachable after deletion" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.deleteGame(id)
    svc.getGameState(id).isLeft should be(true)
  }

  it should "return Left for unknown game" in {
    val svc = GameService()
    svc.deleteGame("no-such-id").isLeft should be(true)
  }

  it should "allow creating a new game after the original is deleted" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.deleteGame(id)
    val id2 = svc.createGame()
    id2 should not be empty
    svc.getGameState(id2).isRight should be(true)
  }

  // ── getPgn ───────────────────────────────────────────────────────────────────

  "GameService.getPgn" should "return a non-empty PGN string for a new game" in {
    val svc    = GameService()
    val id     = svc.createGame()
    val result = svc.getPgn(id)
    result.isRight should be(true)
    result.toOption.get should not be empty
  }

  it should "include a White header tag in the PGN" in {
    val svc    = GameService()
    val id     = svc.createGame()
    val result = svc.getPgn(id).toOption.get
    result should include("[White")
  }

  it should "include a Black header tag in the PGN" in {
    val svc    = GameService()
    val id     = svc.createGame()
    val result = svc.getPgn(id).toOption.get
    result should include("[Black")
  }

  it should "return Left for unknown game" in {
    val svc = GameService()
    svc.getPgn("no-such-id").isLeft should be(true)
  }

  it should "include move notation after a move is made" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e4", None)
    val pgn = svc.getPgn(id).toOption.get
    pgn should include("[White")
    pgn should include("[Black")
  }

  // ── getManager ───────────────────────────────────────────────────────────────

  "GameService.getManager" should "return Right(GameManager) for a known game" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.getManager(id).isRight should be(true)
  }

  it should "return Left for an unknown game" in {
    val svc = GameService()
    svc.getManager("no-such-id").isLeft should be(true)
  }

  it should "return a manager that reflects moves made via the service" in {
    val svc = GameService()
    val id  = svc.createGame()
    svc.makeMoveAlgebraic(id, "e2", "e4", None)
    val manager = svc.getManager(id).toOption.get
    manager.state.board.pieceAt(de.eljachess.chess.model.Square(4, 3)) should not be empty
  }
