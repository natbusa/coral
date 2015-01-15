package com.natalinobusa.coral.app

// the service, actors and paths

import akka.actor.Actor
import spray.routing.HttpService
import spray.util._
import spray.http._

class ApiServiceActor extends Actor with ApiService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing,
  // timeout handling or alternative handler registration
  def receive = runRoute(route)
}

// Routing embedded in the actor
trait ApiService extends HttpService {

  // default logging
  implicit val log = LoggingContext.fromActorRefFactory

  val webappRoute = {
    pathSingleSlash {
      redirect("webapp/", StatusCodes.PermanentRedirect)
    } ~
    pathPrefix("webapp") {
      pathEnd {
        redirect("webapp/", StatusCodes.PermanentRedirect)
      } ~
      pathEndOrSingleSlash {
        getFromResource("webapp/index.html")
      } ~
      getFromResourceDirectory("webapp")
    }
  }

  val route = webappRoute
}
