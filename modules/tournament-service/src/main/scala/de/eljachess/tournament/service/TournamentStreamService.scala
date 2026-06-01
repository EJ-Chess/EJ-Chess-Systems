package de.eljachess.tournament.service

import de.eljachess.tournament.dto.TournamentEvent
import jakarta.enterprise.context.ApplicationScoped
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import java.io.PrintWriter
import java.util.concurrent.CopyOnWriteArrayList
import scala.collection.concurrent.TrieMap

@ApplicationScoped
class TournamentStreamService:
  // Per-tournament ordered event log + finished flag
  private val eventLog: TrieMap[String, CopyOnWriteArrayList[String]] = TrieMap.empty
  private val finished: TrieMap[String, Boolean] = TrieMap.empty

  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  /** Initialize event log for a tournament. */
  def initTournament(id: String): Unit =
    eventLog(id) = new CopyOnWriteArrayList[String]()
    finished(id) = false

  /** Publish an event to the tournament's event log.
    *
    * If event.type == "tournamentFinished", marks tournament as complete.
    */
  def publish(id: String, event: TournamentEvent): Unit =
    val json = mapper.writeValueAsString(event)
    eventLog.get(id).foreach(_.add(json))
    if event.`type` == "tournamentFinished" then
      finished(id) = true

  /** Stream all events (past + future) to the output, blocking until tournament ends.
    *
    * Writes NDJSON (one JSON object per line) to the PrintWriter.
    * Polls for new events every 500ms until tournament is finished.
    */
  def streamTo(id: String, writer: PrintWriter): Unit =
    val log = eventLog.getOrElse(id, new CopyOnWriteArrayList[String]())
    var idx = 0

    while !finished.getOrElse(id, true) || idx < log.size() do
      // Write all new events
      while idx < log.size() do
        writer.println(log.get(idx))
        writer.flush()
        idx += 1

      // Poll for more events if tournament not finished
      if !finished.getOrElse(id, true) then
        Thread.sleep(500)
