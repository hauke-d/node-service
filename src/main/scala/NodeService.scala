
import NodeError.{NodeError, NodeNotFound}
import NodeRepository.NodeProperties
import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.{HttpRoutes, HttpService, MessageFailure, Response}
import org.http4s.dsl.Http4sDsl

case class ErrorResponse(error: String)

class NodeService(nodeRepository: NodeRepository) extends Http4sDsl[IO] {
  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "nodes" / "root" =>
      nodeRepository.findRootNode() flatMap {
        node => Ok(node.asJson)
      }
    case GET -> Root / "nodes" / LongVar(id) =>
      nodeRepository.findNode(id) flatMap {
        node => Ok(node.asJson)
      } handleErrorWith errorHandler

    case GET -> Root / "nodes" / LongVar(id) / "children" =>
      nodeRepository.findChildren(id) flatMap {
        nodes => Ok(nodes.asJson)
      } handleErrorWith errorHandler

    case req @ POST -> Root / "nodes" =>
      (for {
        props <- req.decodeJson[NodeProperties]
        node <- nodeRepository.insertNode(props)
      } yield node) flatMap {
        node => Created(node.asJson)
      } handleErrorWith errorHandler

    case req @ PATCH -> Root / "nodes" / LongVar(id) =>
      (for {
        props <- req.decodeJson[NodeProperties]
        node <- nodeRepository.updateNode(id, props)
      } yield node) flatMap {
        node => Ok(node.asJson)
      } handleErrorWith errorHandler
  }

  def errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = {
    case m: MessageFailure =>
      BadRequest(ErrorResponse("Invalid request format.").asJson)
    case n: NodeError => n match {
      case _: NodeNotFound =>
        NotFound(ErrorResponse(n.msg).asJson)
      case _: NodeError =>
        BadRequest(ErrorResponse(n.msg).asJson)
    }
    case _: Throwable =>
      InternalServerError(ErrorResponse("Internal error.").asJson)
  }
}
