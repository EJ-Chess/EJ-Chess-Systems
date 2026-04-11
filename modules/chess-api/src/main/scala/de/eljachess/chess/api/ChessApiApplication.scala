package de.eljachess.chess.api

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.annotations.QuarkusMain

@QuarkusMain
object ChessApiApplication:
  def main(args: Array[String]): Unit =
    Quarkus.run(args*)
