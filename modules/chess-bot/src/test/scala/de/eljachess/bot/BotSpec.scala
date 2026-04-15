package de.eljachess.bot

import de.eljachess.chess.model.{Board, Color, Piece, PieceKind, Square}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BotSpec extends AnyFlatSpec with Matchers:

  "GreedyRandomBot" should "return a legal move in initial position" in {
    val bot = GreedyRandomBot(EloLevel.Intermediate)
    val move = bot.nextMove(Board.initial, Color.White)
    move shouldBe defined
    val (from, to) = move.get
    Board.initial.move(from, to) shouldBe defined
  }

  it should "return None in checkmate" in {
    // Black king a8, white queen b6, white rook c8
    val grid = Map(
      Square(1, 5) -> Piece(Color.White, PieceKind.Queen),
      Square(2, 7) -> Piece(Color.White, PieceKind.Rook),
      Square(0, 7) -> Piece(Color.Black, PieceKind.King)
    )
    val board = Board(grid)
    val bot = GreedyRandomBot(EloLevel.Intermediate)
    bot.nextMove(board, Color.Black) shouldBe None
  }

  it should "prefer capturing high-value pieces at Advanced level" in {
    // Black pawn on d6 (col=3, row=5) can capture White queen on e5 (col=4, row=4)
    // Black moves downward (row decreasing), so diagonal capture: (3,5) -> (4,4) is valid
    val grid = Map(
      Square(3, 5) -> Piece(Color.Black, PieceKind.Pawn),
      Square(4, 4) -> Piece(Color.White, PieceKind.Queen),
      Square(4, 0) -> Piece(Color.White, PieceKind.King),
      Square(4, 7) -> Piece(Color.Black, PieceKind.King)
    )
    val board = Board(grid)
    val bot = GreedyRandomBot(EloLevel.Advanced)
    val move = bot.nextMove(board, Color.Black)
    move shouldBe Some((Square(3, 5), Square(4, 4)))
  }

  it should "return a move for Beginner level" in {
    val bot = GreedyRandomBot(EloLevel.Beginner)
    val move = bot.nextMove(Board.initial, Color.Black)
    move shouldBe defined
    val (from, to) = move.get
    Board.initial.move(from, to) shouldBe defined
  }

  it should "return a move for custom ELO" in {
    val bot = GreedyRandomBot(EloLevel.custom(1200))
    val move = bot.nextMove(Board.initial, Color.White)
    move shouldBe defined
  }

  it should "report correct elo" in {
    val bot = GreedyRandomBot(EloLevel.Intermediate)
    bot.elo shouldBe 1400
  }
