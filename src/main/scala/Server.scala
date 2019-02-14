import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.implicits._

object Server extends IOApp {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      config <- Config.load()
      exitCode <- Database.transactor(config.database).use { xa =>
        for {
          _ <- Database.initialize(xa)
          exitCode <- BlazeServerBuilder[IO]
            .bindHttp(8080, config.server.host)
            .withHttpApp(Router("/" -> new NodeService(new NodeRepository(xa)).service).orNotFound)
            .serve
            .compile
            .drain
            .flatMap(_ => IO.pure(ExitCode.Success))
        } yield exitCode
      }
    } yield exitCode
  }
}
