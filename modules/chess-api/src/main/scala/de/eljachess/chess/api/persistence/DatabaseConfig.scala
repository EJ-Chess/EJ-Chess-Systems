package de.eljachess.chess.api.persistence

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import slick.jdbc.{H2Profile, JdbcProfile, PostgresProfile}
import slick.jdbc.JdbcBackend.Database
import javax.sql.DataSource
import scala.concurrent.Await
import scala.concurrent.duration.*

/**
 * Slick database configuration.
 *
 * Uses the Quarkus-managed DataSource (Agroal) so Slick does not create
 * a second connection pool. Profile is auto-detected from the JDBC URL.
 */
@ApplicationScoped
class DatabaseConfig:

  @Inject
  var dataSource: DataSource = uninitialized

  private[persistence] var _db:      Database    = uninitialized
  private[persistence] var _tables:  Tables      = uninitialized
  private[persistence] var _profile: JdbcProfile = uninitialized

  @PostConstruct
  def init(): Unit =
    val conn = dataSource.getConnection()
    val url  = conn.getMetaData.getURL
    conn.close()

    _profile = if url.startsWith("jdbc:h2") then H2Profile else PostgresProfile
    _tables  = Tables(_profile)
    _db      = Database.forDataSource(dataSource, None)

    Await.result(_db.run(_tables.createSchemaAction), 10.seconds)

  def db:      Database    = _db
  def tables:  Tables      = _tables
  def profile: JdbcProfile = _profile
