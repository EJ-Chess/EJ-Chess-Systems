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
    val seen = scala.collection.mutable.Set.empty[String]
    val result = scala.collection.mutable.Map.empty[String, String]
    for line <- lines do
      line.trim match
        case tagRegex(tag, value) =>
          if seen.contains(tag) then return Left(s"Duplicate PGN tag: $tag")
          seen += tag
          result(tag) = value
        case other =>
          return Left(s"Invalid PGN header: $other")
    Right(result.toMap)

  private def parseMoves(text: String): List[String] =
    val noComments = text.replaceAll("""\{[^}]*\}""", "")
    val noAnnotations = noComments.replaceAll("[!?]+", "")
    noAnnotations.split("\\s+").toList
      .filter(_.nonEmpty)
      .filterNot(t => t.matches("\\d+\\.+") || t.matches("""(\*|1-0|0-1|1/2-1/2)"""))
