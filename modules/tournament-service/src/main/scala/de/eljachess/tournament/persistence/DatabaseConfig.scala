package de.eljachess.tournament.persistence

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import slick.jdbc.JdbcProfile
import slick.jdbc.H2Profile.api.*
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.compiletime.uninitialized
import java.util.concurrent.TimeUnit

@ApplicationScoped
class DatabaseConfig:

  @ConfigProperty(name = "quarkus.datasource.jdbc.url")
  var jdbcUrl: String = uninitialized

  @ConfigProperty(name = "quarkus.datasource.username")
  var username: String = uninitialized

  @ConfigProperty(name = "quarkus.datasource.password")
  var password: String = uninitialized

  private var _db: Option[slick.jdbc.JdbcBackend.Database]  = None
  private var _tables: Option[Tables]                       = None
  private var _profile: Option[JdbcProfile]                 = None

  /** Detect H2 vs PostgreSQL from JDBC URL prefix. */
  private def detectProfile(): JdbcProfile =
    if jdbcUrl.contains("h2:") then
      slick.jdbc.H2Profile
    else if jdbcUrl.contains("postgresql") then
      slick.jdbc.PostgresProfile
    else
      throw new RuntimeException(s"Unsupported JDBC URL: $jdbcUrl")

  def init(@Observes event: StartupEvent): Unit =
    synchronized {
      val profile = detectProfile()
      _profile = Some(profile)

      val db = if jdbcUrl.contains("h2:") then
        slick.jdbc.JdbcBackend.Database.forURL(
          url = jdbcUrl,
          user = username,
          password = password,
          driver = "org.h2.Driver"
        )
      else
        slick.jdbc.JdbcBackend.Database.forURL(
          url = jdbcUrl,
          user = username,
          password = password,
          driver = "org.postgresql.Driver"
        )

      _db = Some(db)
      _tables = Some(new Tables(profile))

      // Create schema
      val tables = _tables.get
      val createAction = tables.createSchemaAction
      try
        Await.result(db.run(createAction), 10.seconds)
        println("DatabaseConfig.init() — schema ready")
      catch
        case e: Exception =>
          println(s"DatabaseConfig.init() — WARNING: schema creation failed: $e")
          throw e
    }

  // Expose as lazy val to create a stable path for type annotations
  lazy val db: slick.jdbc.JdbcBackend.Database =
    _db.getOrElse(throw new RuntimeException("Database not initialized"))

  lazy val tables: Tables =
    _tables.getOrElse(throw new RuntimeException("Tables not initialized"))

  lazy val profile: JdbcProfile =
    _profile.getOrElse(throw new RuntimeException("Profile not initialized"))
