package de.eljachess.chess.api.persistence

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import slick.jdbc.{H2Profile, JdbcProfile, PostgresProfile}
import slick.jdbc.JdbcBackend.Database
import scala.concurrent.Await
import scala.concurrent.duration.*

/**
 * Slick database configuration.
 *
 * Creates its own Slick connection pool via Database.forURL so it is
 * independent of the Quarkus/Agroal driver binding (which is build-time).
 * Profile is auto-detected from the JDBC URL at startup.
 *
 * In Docker with PostgreSQL: set env vars
 *   QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/chess
 *   QUARKUS_DATASOURCE_USERNAME=chess
 *   QUARKUS_DATASOURCE_PASSWORD=chess
 */
@ApplicationScoped
class DatabaseConfig:

  private val log = Logger.getLogger(classOf[DatabaseConfig])

  @ConfigProperty(name = "quarkus.datasource.jdbc.url")
  var jdbcUrl: String = uninitialized

  @ConfigProperty(name = "quarkus.datasource.username", defaultValue = "sa")
  var username: String = uninitialized

  @ConfigProperty(name = "quarkus.datasource.password", defaultValue = "")
  var password: String = uninitialized

  private[persistence] var _db:      Database    = uninitialized
  private[persistence] var _tables:  Tables      = uninitialized
  private[persistence] var _profile: JdbcProfile = uninitialized

  def init(@Observes event: StartupEvent): Unit =
    log.infof("DatabaseConfig.init() — JDBC URL = %s", jdbcUrl)
    try
      _profile = if jdbcUrl.startsWith("jdbc:h2") then H2Profile else PostgresProfile
      _tables  = Tables(_profile)
      _db      = Database.forURL(jdbcUrl, user = username, password = password)

      Await.result(_db.run(_tables.createSchemaAction), 10.seconds)
      log.info("DatabaseConfig: schema ready")
    catch
      case e: Exception =>
        log.errorf(e, "DatabaseConfig.init() failed: %s", e.getMessage)
        throw e

  def db:      Database    = _db
  def tables:  Tables      = _tables
  def profile: JdbcProfile = _profile
