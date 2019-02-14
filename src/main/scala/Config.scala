import cats.effect.IO
import com.typesafe.config.ConfigFactory
import pureconfig.error.ConfigReaderException

object Config {

  case class ServerConfig(host: String ,port: Int)

  case class DatabaseConfig(driver: String, url: String, user: String, password: String, connectionPoolSize: Int)

  case class Config(server: ServerConfig, database: DatabaseConfig)

  import pureconfig._

  def load(fileName: String = "application.conf"): IO[Config] = {
    IO {
      loadConfig[Config](ConfigFactory.load(fileName))
    }.flatMap {
      case Left(e) => IO.raiseError[Config](new ConfigReaderException[Config](e))
      case Right(config) => IO.pure(config)
    }
  }
}
