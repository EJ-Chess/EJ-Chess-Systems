// core/src/main/scala/de/eljachess/chess/model/Pgn.scala
package de.eljachess.chess.model

object Pgn:

  def decode(text: String): Either[String, (Map[String, String], List[String])] =
    val lines = text.split("\r?\n").toList
    val (headerLines, restLines) = lines.span(l => l.trim.startsWith("[") || l.trim.isEmpty)
    val nonEmptyHeaders = headerLines.filter(_.trim.nonEmpty)

    parseHeaders(nonEmptyHeaders).map { headers =>
      val moveText = restLines.mkString(" ")
      val moves = parseMoves(moveText)
      (headers, moves)
    }

  private def parseHeaders(lines: List[String]): Either[String, Map[String, String]] =
    val tagRegex = """^\[(\w+)\s+"(.*)"\]$""".r
    lines.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
      case (Left(err), _) => Left(err)
      case (Right(acc), line) =>
        line.trim match
          case tagRegex(tag, value) =>
            if acc.contains(tag) then Left(s"Duplicate PGN tag: $tag")
            else Right(acc + (tag -> value))
          case other =>
            Left(s"Invalid PGN header: $other")
    }

  private def parseMoves(text: String): List[String] =
    val noComments    = text.replaceAll("""\{[^}]*\}""", "")
    val noAnnotations = noComments.replaceAll("[!?]+", "")
    val tokens        = noAnnotations.split("\\s+").toList.filter(_.nonEmpty)
    val moveNumbers   = """^\d+\.+$""".r
    val results       = Set("*", "1-0", "0-1", "1/2-1/2")
    tokens.filterNot(t => moveNumbers.matches(t) || results.contains(t))
