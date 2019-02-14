import cats.effect.IO
import doobie._
import doobie.implicits._

object NodeError {
  sealed trait NodeError extends Exception {
    def msg: String
  }
  case class NodeNotFound(id: Long) extends NodeError {
    def msg = s"Node with id $id does not exist."
  }
  case object NewParentIsDescendant extends NodeError {
    def msg = "Cannot change the parent to a descendant of the node."
  }
}

object NodeRepository {
  case class NodeProperties(name: String, parentId: Long)

  case class Node(id: Long, name: String, parentId: Option[Long], rootId: Long, height: Int)
}

class NodeRepository(transactor: Transactor[IO]) {
  import NodeRepository._
  import NodeError._

  def getRootNodeSql(): ConnectionIO[Node] = {
    sql"select id, name, parent_id, root_id, height from node where parent_id is null"
      .query[Node]
      .unique
  }

  def getNodeSql(id: Long): ConnectionIO[Option[Node]] = {
    sql"select id, name, parent_id, root_id, height from node where id = $id"
      .query[Node]
      .option
  }

  def getChildrenSql(id: Long): ConnectionIO[List[Node]] = {
    sql"""
      select id, name, parent_id, root_id, height from node where parent_id = $id
      """
      .query[Node]
      .to[List]
  }

  def insertNodeSql(node: NodeProperties): ConnectionIO[Node] = {
    sql"""
      insert into node (name, parent_id, root_id, height)
      values (
        ${node.name},
        ${node.parentId},
        (select root_id from node where id = ${node.parentId}),
        (select height + 1 from node where id = ${node.parentId})
      )
      """
      .update
      .withUniqueGeneratedKeys[Node]("id", "name", "parent_id", "root_id", "height")
  }

  def updateNodeSql(id: Long, node: NodeProperties): ConnectionIO[Node] = {
    sql"""
      update node
      set
        name = ${node.name},
        parent_id = ${node.parentId},
        height = (select height + 1 from node where id = ${node.parentId})
      where id = $id
      """
      .update
      .withUniqueGeneratedKeys[Node]("id", "name", "parent_id", "root_id", "height")
  }

  def updateDescendantHeightsSql(id: Long): ConnectionIO[Int] = {
    sql"""
      with recursive children as (
        select id, height from node where id = 1
        union
        select node.id, children.height+1 from node
	      join children on node.parent_id = children.id
      )
      update node
      set height = children.height
      from children
      where children.id = node.id
      """
      .update
      .run
  }

  def isDescendant(node: Node, possibleDescendant: Node): IO[Boolean] = {
    sql"""
       with recursive parents as (
        select id, parent_id from node where id = ${possibleDescendant.id}
        union
        select node.id, node.parent_id from node
        join parents on parents.parent_id = node.id
        where node.parent_id is not null
       )
       select count(*) > 0 from parents where id = ${node.id}
      """
      .query[Boolean]
      .unique
      .transact(transactor)
  }

  def findNode(id: Long): IO[Node] = {
    getNodeSql(id).transact(transactor) flatMap {
      case None => IO.raiseError(NodeNotFound(id))
      case Some(n) => IO.pure(n)
    }
  }

  def findRootNode(): IO[Node] = {
    getRootNodeSql().transact(transactor)
  }

  def findChildren(parentId: Long): IO[List[Node]] = {
    for {
      _ <- findNode(parentId)
      children <- getChildrenSql(parentId).transact(transactor)
    } yield children
  }

  def insertNode(node: NodeProperties) = {
    for {
      _ <- findNode(node.parentId)
      node <- insertNodeSql(node).transact(transactor)
    } yield node
  }

  def updateNode(id: Long, node: NodeProperties): IO[Node] = {
    // Run updates in one transaction
    def updateNodeAndHeights: ConnectionIO[Node] = for {
        updatedNode <- updateNodeSql(id, node)
        _ <- updateDescendantHeightsSql(updatedNode.id)
      } yield updatedNode

    // Changing parent to a descendant would break tree constraint
    def ensureNotDescendant(n: Node, p: Node): IO[Unit] =
      isDescendant(n, p) flatMap {
        case false => IO.pure(())
        case true => IO.raiseError(NewParentIsDescendant)
      }

    for {
      n <- findNode(id)
      p <- findNode(node.parentId)
      _ <- ensureNotDescendant(n, p)
      node <- updateNodeAndHeights.transact(transactor)
    } yield node
  }
}

