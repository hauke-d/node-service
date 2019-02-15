
import NodeError.{NodeError, NodeNotFound}
import NodeRepository.{NodeProperties, UpdateNodeProperties}
import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.{HttpRoutes, HttpService, MessageFailure, Response}
import org.http4s.dsl.Http4sDsl

case class ErrorResponse(error: String)

class NodeService(nodeRepository: NodeRepository) extends Http4sDsl[IO] {
  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // Get the root node
    case GET -> Root / "nodes" / "root" =>
      nodeRepository.findRootNode() flatMap {
        node => Ok(node.asJson)
      }

    // Get a node by id
    case GET -> Root / "nodes" / LongVar(id) =>
      nodeRepository.findNode(id) flatMap {
        node => Ok(node.asJson)
      } handleErrorWith errorHandler

    // Get the children of a node with a given id
    case GET -> Root / "nodes" / LongVar(id) / "children" =>
      nodeRepository.findChildren(id) flatMap {
        nodes => Ok(nodes.asJson)
      } handleErrorWith errorHandler

    // Create a new node
    case req @ POST -> Root / "nodes" =>
      (for {
        props <- req.decodeJson[NodeProperties]
        node <- nodeRepository.insertNode(props)
      } yield node) flatMap {
        node => Created(node.asJson)
      } handleErrorWith errorHandler

    // Change the parent (and/or name) of a node
    case req @ PATCH -> Root / "nodes" / LongVar(id) =>
      (for {
        props <- req.decodeJson[UpdateNodeProperties]
        node <- nodeRepository.updateNode(id, props)
      } yield node) flatMap {
        node => Ok(node.asJson)
      } handleErrorWith errorHandler
  }

  /**
    * Generic error handler for all request types.
    */
  def errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = {
    // Validation errors
    case n: NodeError => n match {
      case _: NodeNotFound =>
        NotFound(ErrorResponse(n.msg).asJson)
      case _: NodeError =>
        BadRequest(ErrorResponse(n.msg).asJson)
    }
    // Could not make sense of the request
    case _: MessageFailure =>
      BadRequest(ErrorResponse("Invalid request format.").asJson)
    // Anything unexpected
    case _: Throwable =>
      InternalServerError(ErrorResponse("Internal error.").asJson)
  }
}
