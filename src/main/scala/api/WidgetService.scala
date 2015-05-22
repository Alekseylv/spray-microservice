package api

import akka.actor.{ActorRefFactory, ActorSystem}
import api.handler.{UserRequest, OrderRequest, WidgetHandler}
import com.github.vonnagy.service.container.http.routing.RoutedEndpoints
import spray.http.MediaTypes._

class WidgetService(implicit val system: ActorSystem,
                    actorRefFactory: ActorRefFactory) extends RoutedEndpoints with WidgetHandler {

  // Import the default Json marshaller and un-marshaller

  val route = {
    pathPrefix("order") {
      (get & path(Segment)) {
        orderId =>
          respondWithMediaType(`application/json`) {
            fetchOrder(orderId)
          }
      }
    } ~
      (post & entity(as[OrderRequest])) {
        orderRequest =>
          respondWithMediaType(`application/json`) {
            createOrder(orderRequest)
          }
      }
  } ~
    pathPrefix("user") {
      (get & path(Segment)) {
        userId =>
          respondWithMediaType(`application/json`) {
            fetchUser(userId)
          }
      } ~
        (post & entity(as[UserRequest])) {
          userRequest =>
            respondWithMediaType(`application/json`) {
              createUser(userRequest)
            }
        }
    }
}