package de.eljachess.tournament.service

import de.eljachess.tournament.persistence.Tables
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import slick.jdbc.H2Profile

class SwissServiceSpec extends AnyFlatSpec with Matchers:
  val service = new SwissService()
  val tables = new Tables(H2Profile)

  "SwissService" should "pair 4 players in round 1 (no rematches possible)" in {
    val players = List(
      tables.PlayerRow("t1", "bot1", "Bot1", 0.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot2", "Bot2", 0.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot3", "Bot3", 0.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot4", "Bot4", 0.0, 0.0, 0, 0, 0, 0, 0)
    )
    val pairings = service.computePairings(players, Nil)
    pairings should have length 2
  }

  it should "sort players by points desc before pairing" in {
    val players = List(
      tables.PlayerRow("t1", "bot1", "Bot1", 0.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot2", "Bot2", 2.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot3", "Bot3", 1.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot4", "Bot4", 1.5, 0.0, 0, 0, 0, 0, 0)
    )
    val pairings = service.computePairings(players, Nil)
    pairings should have length 2
    // bot2 (2.0 pts) should be paired first
  }

  it should "assign colors: player with lower colorBalance gets white" in {
    val players = List(
      tables.PlayerRow("t1", "bot1", "Bot1", 1.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot2", "Bot2", 1.0, 0.0, 0, 0, 0, 0, 2)  // bot2 has colorBalance 2
    )
    val pairings = service.computePairings(players, Nil)
    // bot1 should get white because it has lower colorBalance (0 < 2)
    pairings should have length 1
    pairings(0)._1.botId should equal("bot1")  // bot1 is white
    pairings(0)._2.botId should equal("bot2")  // bot2 is black
  }

  it should "compute Buchholz score (sum of opponents' points)" in {
    val pairings = List(
      tables.PairingRow("p1", "t1", 1, "bot1", "Bot1", "bot2", "Bot2", Some("g1"), None),
      tables.PairingRow("p2", "t1", 1, "bot3", "Bot3", "bot1", "Bot1", Some("g2"), None)
    )
    val players = List(
      tables.PlayerRow("t1", "bot1", "Bot1", 0.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot2", "Bot2", 1.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot3", "Bot3", 0.5, 0.0, 0, 0, 0, 0, 0)
    )
    val tb = service.buchholz("bot1", pairings, players)
    tb should equal(1.5)  // bot1 played bot2 (1.0) and bot3 (0.5)
  }

  it should "handle odd number of players (last gets bye)" in {
    val players = List(
      tables.PlayerRow("t1", "bot1", "Bot1", 0.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot2", "Bot2", 0.0, 0.0, 0, 0, 0, 0, 0),
      tables.PlayerRow("t1", "bot3", "Bot3", 0.0, 0.0, 0, 0, 0, 0, 0)
    )
    val pairings = service.computePairings(players, Nil)
    pairings should have length 1  // 3 players: 1 pairing + 1 bye
  }
