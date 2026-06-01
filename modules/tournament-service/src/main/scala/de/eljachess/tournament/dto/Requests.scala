package de.eljachess.tournament.dto

// Tournament management
case class CreateTournamentRequest(
  name: String,
  nbRounds: Int,
  clockLimit: Int,
  clockIncrement: Int,
  rated: Option[Boolean] = None
)

// Participation
case class JoinRequest()

case class WithdrawRequest()
