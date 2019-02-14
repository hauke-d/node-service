import Config.DatabaseConfig
import cats.effect.{ContextShift, IO, Resource}
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

object Database {
  def transactor(config: DatabaseConfig)(implicit ec: ExecutionContext): Resource[IO, HikariTransactor[IO]] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    HikariTransactor.newHikariTransactor[IO](config.driver, config.url, config.user, config.password, ec, ec)
  }

  def initialize(transactor: HikariTransactor[IO]): IO[Unit] = {
    transactor.configure { source =>
      IO {
        val flyWay = new Flyway()
        flyWay.setDataSource(source)
        flyWay.migrate()
      }
    }
  }
}
