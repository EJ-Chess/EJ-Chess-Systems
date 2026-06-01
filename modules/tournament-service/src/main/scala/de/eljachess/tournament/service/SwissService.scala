package de.eljachess.tournament.service

import de.eljachess.tournament.persistence.Tables
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SwissService:

  /** Compute round N pairings using Swiss system.
    *
    * Algorithm:
    * 1. Sort players by (points desc, tieBreak desc)
    * 2. Split: top half vs bottom half
    * 3. Pair top[i] vs bottom[i]
    * 4. Check for rematches — if rematch detected, slide opponent down list
    * 5. Assign colors: give White to player with lower colorBalance (fewer whites)
    * 6. If odd number: last player gets bye (1 point, no game)
    */
  def computePairings(
    players: List[Tables#PlayerRow],
    pastPairings: List[Tables#PairingRow]
  ): List[(Tables#PlayerRow, Tables#PlayerRow)] =
    if players.length < 2 then return List()

    val sorted = players.sortBy(p => (-p.points, -p.tieBreak))
    val isOdd = sorted.length % 2 != 0
    val eligible = if isOdd then sorted.dropRight(1) else sorted

    val paired = pairHalves(eligible, pastPairings)
    paired.map { case (a, b) => assignColors(a, b) }

  /** Pair top half vs bottom half, checking for rematches. */
  private def pairHalves(
    sorted: List[Tables#PlayerRow],
    past: List[Tables#PairingRow]
  ): List[(Tables#PlayerRow, Tables#PlayerRow)] =
    val half = sorted.length / 2
    val top = sorted.take(half)
    val bot = sorted.drop(half)

    top.zip(bot).map { case (a, b) =>
      if hasPlayed(a.botId, b.botId, past) then
        // Try to find an alternative opponent for a in bottom half
        val alt = bot.find(x => x.botId != b.botId && !hasPlayed(a.botId, x.botId, past))
        (a, alt.getOrElse(b))  // fallback to original if no alternative found
      else (a, b)
    }

  /** Check if two players have already played each other. */
  private def hasPlayed(a: String, b: String, past: List[Tables#PairingRow]): Boolean =
    past.exists(p => (p.whiteId == a && p.blackId == b) || (p.whiteId == b && p.blackId == a))

  /** Assign colors: give White to player with lower colorBalance (fewer whites taken so far).
    *
    * Returns (white, black).
    */
  private def assignColors(
    a: Tables#PlayerRow,
    b: Tables#PlayerRow
  ): (Tables#PlayerRow, Tables#PlayerRow) =
    if a.colorBalance <= b.colorBalance then (a, b)  // a gets white
    else (b, a)  // b gets white

  /** Compute Buchholz score (sum of all opponents' current points). */
  def buchholz(
    botId: String,
    allPairings: List[Tables#PairingRow],
    allPlayers: List[Tables#PlayerRow]
  ): Double =
    val opponentIds = allPairings
      .filter(p => p.whiteId == botId || p.blackId == botId)
      .map(p => if p.whiteId == botId then p.blackId else p.whiteId)

    opponentIds
      .flatMap(id => allPlayers.find(_.botId == id).map(_.points))
      .sum
