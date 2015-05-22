package api.handler

import akka.actor.ActorSystem
import com.github.vonnagy.service.container.http.routing.Rejection.{DuplicateRejection, NotFoundRejection}
import db.WidgetPersistence
import model.Widget
import spray.http.StatusCodes._
import scala.util.{Failure, Success}
import spray.httpx.marshalling.{Marshaller, ToResponseMarshaller}
import spray.routing._
import java.util.UUID
import java.io.IOException
import scala.concurrent.{ExecutionContextExecutor, Future}
import java.util.concurrent.ConcurrentHashMap
import spray.client.pipelining._
import spray.util._
import com.github.vonnagy.service.container.http.DefaultMarshallers

trait WidgetHandler { self:DefaultMarshallers =>

  implicit val system: ActorSystem
  import system.dispatcher


  implicit val unmarshaller = jsonUnmarshaller[OrderRequest]
  implicit val unmarshaller2 = jsonUnmarshaller[UserRequest]
  implicit val unmarshaller3 = jsonUnmarshaller[User]
  implicit val marshaller = jsonMarshaller


  lazy val orders = new ConcurrentHashMap[String, OrderInner]
  lazy val users = new ConcurrentHashMap[String, User]

  val pipeline = sendReceive ~> unmarshal[User]
  val pipeline2 = sendReceive ~> unmarshal[String]

  def createOrder[T](order: OrderRequest)(implicit marshaller: Marshaller[T]): Route = ctx => {
    implicit def userRequestMarshaller = marshaller.asInstanceOf[Marshaller[UserRequest]]

      val future = pipeline2 {
        Post("http://localhost:9000/user/", order.user)
      }
    future.foreach(x => orders.put(x, OrderInner(order, x)))

    future onSuccess {
      case uuid:String => ctx.complete(uuid)
    }

    future onFailure {
      case _ => ctx.reject(NotFoundRejection(s"bad order create request"))
    }
  }

  def getOrder(uuid:String):Option[OrderInner] = Option(orders.get(uuid))


  def fetchOrder[T](uuid: String)(implicit marshaller: Marshaller[T]): Route = ctx => {
    val order = getOrder(uuid).map { orderInner =>
      val future:Future[User] = pipeline {
        Get(s"http://localhost:9000/user/$uuid")
      }
      future.map(x => Order(orderInner, x))
    }

    order match {
      case Some(future) =>
        future onSuccess {
          case orderResult =>
            implicit def orderMarshaller = marshaller.asInstanceOf[Marshaller[Order]]
            ctx.complete(orderResult)
        }
        future onFailure { case x =>
          ctx.reject(NotFoundRejection(s"user not found $uuid"))
        }
      case _ => ctx.reject(NotFoundRejection(s"user not found $uuid"))
    }
  }

  def fetchUser[T](uuid: String)(implicit marshaller: Marshaller[T]): Route = ctx => {
    val user = users.get(uuid)
    if (user != null)  {
      implicit def x = marshaller.asInstanceOf[Marshaller[User]]
      ctx.complete(user)
    } else {
      ctx.reject(NotFoundRejection(s"user not found $uuid"))
    }
  }

  def createUser[T](user: UserRequest)(implicit marshaller: Marshaller[T]): Route = ctx => {
    val uuid = UUID.randomUUID().toString
    users.put(uuid, User(user, uuid))
    ctx.complete(uuid)
  }
}

case class Order(user: User, product: String, deliveryAddress: String, price: Double)

case class User(firstName: String, lastName: String, uuid: String)

case class OrderRequest(user: UserRequest, product: String, deliveryAddress: String, price: Double)

case class UserRequest(firstName: String, lastName: String)

case class OrderInner(product: String, deliveryAddress: String, price: Double, uuid: String)

object Order {
  def apply(order: OrderInner, user: User): Order = Order(user, order.product, order.deliveryAddress, order.price)
}

object OrderInner {
  def apply(order: OrderRequest, uuid: String): OrderInner = OrderInner(order.product, order.deliveryAddress, order.price, uuid)
}

object User {
  def apply(user: UserRequest, uuid: String): User = User(user.firstName, user.lastName, uuid)
}

