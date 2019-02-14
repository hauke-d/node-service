import Config.DatabaseConfig
import NodeError.{NewParentIsDescendant, NodeNotFound}
import NodeRepository.{Node, NodeProperties}
import cats.data.Kleisli
import cats.effect.{ContextShift, IO}
import doobie.hikari.HikariTransactor
import io.circe.generic.auto._
import org.http4s.circe._
import doobie.implicits._
import io.circe.Json
import org.http4s.{EntityBody, EntityDecoder, HttpService, Method, Request, Response, Status, Uri}
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import io.circe.syntax._
import org.http4s.implicits._

import scala.concurrent.ExecutionContext.Implicits.global

class NodeServiceTest extends FunSuite with Matchers with BeforeAndAfterEach {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  // Set up test service
  val conf: DatabaseConfig = Config.load("test.conf").unsafeRunSync().database

  def withTransactor(f: HikariTransactor[IO] => Unit) = {
    val transactor = HikariTransactor.newHikariTransactor[IO](
      conf.driver, conf.url, conf.user, conf.password, global, global
    )
    transactor.use(xa => {
      f.apply(xa)
      IO.pure(())
    }).unsafeRunSync()
  }

  def withService(f: Kleisli[IO, Request[IO], Response[IO]] => Unit) = {
    withTransactor { xa =>
      Database.initialize(xa).unsafeRunSync()
      val service = new NodeService(new NodeRepository(xa)).service.orNotFound
      f.apply(service)
    }
  }

  def body(j: Json): EntityBody[IO] = fs2.Stream.apply(j.toString().getBytes(): _*)

  // Clear database before each case
  override def beforeEach(): Unit = withTransactor { xa =>
    sql"""
      drop schema public cascade;
      create schema public;
      """.update.run.transact(xa).unsafeRunSync()
  }

  // Return true if match succeeds; otherwise false
  def check[A](actual:        IO[Response[IO]],
               expectedStatus: Status,
               expectedBody:   Option[A])(
                implicit ev: EntityDecoder[IO, A]
              ): Unit =  {
    val actualResp         = actual.unsafeRunSync
    val statusCheck        = actualResp.status == expectedStatus
    val bodyCheck          = expectedBody.fold[Boolean](
      actualResp.body.compile.toVector.unsafeRunSync.isEmpty)( // Verify Response's body is empty.
      expected => actualResp.as[A].unsafeRunSync == expected
    )
    if(!statusCheck) {
      new Exception().printStackTrace()
      fail(s"Expected status $expectedStatus, got: ${actualResp.status}")
    }
    if(!bodyCheck) {
      new Exception().printStackTrace()
      fail(s"Expected body:\n$expectedBody, got:\n${actualResp.as[String].unsafeRunSync()}")
    }
  }

  test("Insert and get node") {
    withService { service =>
      check(
        service.run(Request(method = Method.GET, uri = Uri.uri("/nodes/root"))),
        Status.Ok,
        Some(Node(1, "root", None, 1, 0).asJson)
      )

      // Insert with empty body
      check(
        service.run(Request(method = Method.POST, uri = Uri.uri("/nodes"))),
        Status.BadRequest,
        Some(ErrorResponse("Invalid request format.").asJson)
      )
      // Insert with invalid parent
      check(
        service.run(Request(method = Method.POST, uri = Uri.uri("/nodes"), body = body(NodeProperties("a", parentId = 999).asJson))),
        Status.NotFound,
        Some(ErrorResponse(NodeNotFound(999).msg).asJson)
      )
      // Insert valid
      check(
        service.run(Request(method = Method.POST, uri = Uri.uri("/nodes"), body = body(NodeProperties("a", parentId = 1).asJson))),
        Status.Created,
        Some(Node(2, "a", Some(1), 1, 1).asJson)
      )
      // Insert neighboring node (same height)
      check(
        service.run(Request(method = Method.POST, uri = Uri.uri("/nodes"), body = body(NodeProperties("b", parentId = 1).asJson))),
        Status.Created,
        Some(Node(3, "b", Some(1), 1, 1).asJson)
      )

      // Insert another node to see if height is increasing
      check(
        service.run(Request(method = Method.POST, uri = Uri.uri("/nodes"), body = body(NodeProperties("c", parentId = 2).asJson))),
        Status.Created,
        Some(Node(4, "c", Some(2), 1, 2).asJson)
      )

      // Get node
      check(
        service.run(Request(method = Method.GET, uri = Uri.uri("/nodes/2"))),
        Status.Ok,
        Some(Node(2, "a", Some(1), 1, 1).asJson)
      )
      // Try get non-existent node
      check(
        service.run(Request(method = Method.GET, uri = Uri.uri("/nodes/999"))),
        Status.NotFound,
        Some(ErrorResponse(NodeNotFound(999).msg).asJson)
      )
    }
  }

  test("Get children of a node") {
    withService { service =>
      // Starting fresh, root should not have children
      check(
        service.run(Request(method = Method.GET, uri = Uri.uri("/nodes/1/children"))),
        Status.Ok,
        Some(List.empty[Node].asJson)
      )
      // Non existent-node should return error
      check(
        service.run(Request(method = Method.GET, uri = Uri.uri("/nodes/999/children"))),
        Status.NotFound,
        Some(ErrorResponse(NodeNotFound(999).msg).asJson)
      )
      // Insert three children for root
      service.run(Request(method = Method.POST, uri = Uri.uri("/nodes"), body = body(NodeProperties("c1", 1).asJson))).unsafeRunSync()
      service.run(Request(method = Method.POST, uri = Uri.uri("/nodes"), body = body(NodeProperties("c2", 1).asJson))).unsafeRunSync()
      service.run(Request(method = Method.POST, uri = Uri.uri("/nodes"), body = body(NodeProperties("c3", 1).asJson))).unsafeRunSync()

      // Should return all children
      check(
        service.run(Request(method = Method.GET, uri = Uri.uri("/nodes/1/children"))),
        Status.Ok,
        Some(List(
          Node(2, "c1", Some(1), 1, 1),
          Node(3, "c2", Some(1), 1, 1),
          Node(4, "c3", Some(1), 1, 1)
        ).asJson)
      )
    }
  }

  test("Update parent of a node") {
    /** Create basic structure
      *         r
      *      a    b
      *    c  d    e
      *        f
      */
    withService { service =>
      def insert(name: String, parent: Long) =
        service.run(Request(
          method = Method.POST,
          uri = Uri.uri("/nodes"),
          body = body(NodeProperties(name, parent).asJson)
        )).unsafeRunSync()

      def checkNode(uri: Uri, expected: Node) =
        check(
          service.run(Request(method = Method.GET, uri = uri)),
          Status.Ok,
          Some(expected.asJson)
        )

      insert("a", 1)
      insert("b", 1)
      insert("c", 2)
      insert("d", 2)
      insert("e", 3)
      insert("f", 5)

      checkNode(Uri.uri("/nodes/2"), Node(2, "a", Some(1), 1, 1))
      checkNode(Uri.uri("/nodes/3"), Node(3, "b", Some(1), 1, 1))
      checkNode(Uri.uri("/nodes/4"), Node(4, "c", Some(2), 1, 2))
      checkNode(Uri.uri("/nodes/5"), Node(5, "d", Some(2), 1, 2))
      checkNode(Uri.uri("/nodes/6"), Node(6, "e", Some(3), 1, 2))
      checkNode(Uri.uri("/nodes/7"), Node(7, "f", Some(5), 1, 3))

      // Updating a non-existent node should fail
      check(
        service.run(Request(method = Method.PATCH, uri = Uri.uri("/nodes/999"), body = body(NodeProperties("a", 4).asJson))),
        Status.NotFound,
        Some(ErrorResponse(NodeNotFound(999).msg).asJson)
      )

      // Updating the parent to one of the descendants should fail
      check(
        service.run(Request(method = Method.PATCH, uri = Uri.uri("/nodes/2"), body = body(NodeProperties("a", 7).asJson))),
        Status.BadRequest,
        Some(ErrorResponse(NewParentIsDescendant.msg).asJson)
      )

      // Updating the parent to the node itself should fail
      check(
        service.run(Request(method = Method.PATCH, uri = Uri.uri("/nodes/2"), body = body(NodeProperties("a", 2).asJson))),
        Status.BadRequest,
        Some(ErrorResponse(NewParentIsDescendant.msg).asJson)
      )

      // Updating the parent should otherwise succeed
      check(
        service.run(Request(method = Method.PATCH, uri = Uri.uri("/nodes/2"), body = body(NodeProperties("a", 6).asJson))),
        Status.Ok,
        Some(Node(2, "a", Some(6), 1, 3).asJson)
      )

      // Verify that the height on the children was updated correctly
      checkNode(Uri.uri("/nodes/4"), Node(4, "c", Some(2), 1, 4))
      checkNode(Uri.uri("/nodes/5"), Node(5, "d", Some(2), 1, 4))
      checkNode(Uri.uri("/nodes/7"), Node(7, "f", Some(5), 1, 5))

    }
  }
}