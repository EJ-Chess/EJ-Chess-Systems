package de.eljachess.tournament.service

import de.eljachess.tournament.dto.CreateTournamentRequest
import de.eljachess.tournament.persistence.{TournamentRepository, Tables}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TournamentServiceSpec extends AnyFlatSpec with Matchers:

  "TournamentService" should "create a tournament with status=created" in {
    val service = new TournamentService()
    service.repository = new TournamentRepository()
    service.swissService = new SwissService()
    service.streamService = new TournamentStreamService()

    val req = CreateTournamentRequest("Test Tourney", 3, 300, 3, Some(true))
    val result = service.createTournament(req, "director1")

    result should be (a [Right[_, _]])
    result.map(_.status) should equal (Right("created"))
  }

  it should "allow bots to join a tournament in created status" in {
    val service = new TournamentService()
    service.repository = new TournamentRepository()
    service.swissService = new SwissService()
    service.streamService = new TournamentStreamService()

    val req = CreateTournamentRequest("Test Tourney", 3, 300, 3, Some(true))
    val tourRes = service.createTournament(req, "director1")
    val tourId = tourRes.map(_.id).getOrElse("")

    val joinRes = service.joinTournament(tourId, "bot1", "Bot1")
    joinRes should be(Right(()))
  }

  it should "prevent join when tournament already started" in {
    val service = new TournamentService()
    service.repository = new TournamentRepository()
    service.swissService = new SwissService()
    service.streamService = new TournamentStreamService()

    // Create tournament
    val req = CreateTournamentRequest("Test Tourney", 3, 300, 3, Some(true))
    val tourRes = service.createTournament(req, "director1")
    val tourId = tourRes.map(_.id).getOrElse("")

    // Join with 2 bots
    service.joinTournament(tourId, "bot1", "Bot1")
    service.joinTournament(tourId, "bot2", "Bot2")

    // Start tournament
    service.startTournament(tourId, "director1")

    // Try to join a third bot — should fail
    val joinRes = service.joinTournament(tourId, "bot3", "Bot3")
    joinRes should be (a [Left[_, _]])
  }

  it should "allow withdrawal from tournament in created status" in {
    val service = new TournamentService()
    service.repository = new TournamentRepository()
    service.swissService = new SwissService()
    service.streamService = new TournamentStreamService()

    val req = CreateTournamentRequest("Test Tourney", 3, 300, 3, Some(true))
    val tourRes = service.createTournament(req, "director1")
    val tourId = tourRes.map(_.id).getOrElse("")

    service.joinTournament(tourId, "bot1", "Bot1")
    val withdrawRes = service.withdrawFromTournament(tourId, "bot1")
    withdrawRes should be(Right(()))
  }

  it should "fail to start tournament with fewer than 2 bots" in {
    val service = new TournamentService()
    service.repository = new TournamentRepository()
    service.swissService = new SwissService()
    service.streamService = new TournamentStreamService()

    val req = CreateTournamentRequest("Test Tourney", 3, 300, 3, Some(true))
    val tourRes = service.createTournament(req, "director1")
    val tourId = tourRes.map(_.id).getOrElse("")

    service.joinTournament(tourId, "bot1", "Bot1")

    val startRes = service.startTournament(tourId, "director1")
    startRes should be (a [Left[_, _]])
    startRes.fold(err => err should include ("need at least 2 bots"), _ => fail())
  }

  it should "start tournament with 2+ bots" in {
    val service = new TournamentService()
    service.repository = new TournamentRepository()
    service.swissService = new SwissService()
    service.streamService = new TournamentStreamService()

    val req = CreateTournamentRequest("Test Tourney", 3, 300, 3, Some(true))
    val tourRes = service.createTournament(req, "director1")
    val tourId = tourRes.map(_.id).getOrElse("")

    service.joinTournament(tourId, "bot1", "Bot1")
    service.joinTournament(tourId, "bot2", "Bot2")

    val startRes = service.startTournament(tourId, "director1")
    startRes should be (a [Right[_, _]])
    startRes.foreach(_.status should equal ("started"))
  }
