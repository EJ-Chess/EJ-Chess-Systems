package de.eljachess.tournament.auth

import java.util.Base64
import scala.util.Try
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Simple JWT handler for development/testing.
 *
 * In production, this should use proper JWT validation with public keys.
 * For academic use, we extract and trust the `sub` claim from Bearer tokens.
 */
object JwtHandler:
  private val mapper = new ObjectMapper()
  private val decoder = Base64.getUrlDecoder

  /**
   * Extract user ID from JWT Bearer token.
   *
   * JWT format: "Authorization: Bearer <header>.<payload>.<signature>"
   * Payload is base64url-encoded JSON.
   * We extract the `sub` (subject) claim.
   */
  def extractUserId(authHeader: String): Option[String] =
    val token = extractToken(authHeader)
    token.flatMap(extractSubject)

  private def extractToken(auth: String): Option[String] =
    Option(auth)
      .filter(_.startsWith("Bearer "))
      .map(_.stripPrefix("Bearer ").trim)
      .filter(_.nonEmpty)

  private def extractSubject(token: String): Option[String] =
    Try {
      val parts = token.split("\\.")
      if parts.length != 3 then None
      else
        // Decode payload (second part)
        val payload = new String(decoder.decode(parts(1)), "UTF-8")
        val json = mapper.readTree(payload)
        Option(json.get("sub")).map(_.asText()).filter(_.nonEmpty)
    }.toOption.flatten
