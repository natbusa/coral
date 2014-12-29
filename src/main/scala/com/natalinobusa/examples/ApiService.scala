package com.natalinobusa.examples

import org.json4s.JsonAST.{JNothing, JValue}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

// akka
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.{Actor, ActorLogging}

// Actor messaging
import com.natalinobusa.examples.models.Messages.{ListFields, GetField}

// Spray
import spray.http.StatusCodes
import spray.routing.HttpService

// json
import org.json4s.JObject
import com.natalinobusa.examples.models.JsonConversions

class ApiServiceActor extends Actor with ApiService with ActorLogging {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing,
  // timeout handling or alternative handler registration
  def receive = runRoute(serviceRoute)

}

trait ApiService extends HttpService {

  implicit def executionContext = actorRefFactory.dispatcher
  implicit val timeout = Timeout(1.seconds)

  def askActor(a: String, msg:Any)    =  actorRefFactory.actorSelection(a).ask(msg)

  val serviceRoute = {
    pathPrefix("api") {
      pathPrefix("in") {
        post {
          import JsonConversions._
          entity(as[JObject]) { json =>
            actorRefFactory.actorSelection("/user/events") ! json
            complete(StatusCodes.Created, json)
          }
        }
      } ~
      pathPrefix("actors" / Segment / Segment) {
        (name, city) =>
          pathEnd {
            get {
              import JsonConversions._
              val result = askActor(s"/user/events/$name/$city",ListFields).mapTo[JObject]
              onComplete(result) {
                case Success(json) => complete(json)
                case Failure(ex) => complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")

              }
            }
          } ~
          pathPrefix(Segment) {
            field =>
              get {
                import JsonConversions._
                val json = askActor(s"/user/events/$name/$city",GetField(field)).mapTo[JValue]
                onComplete(json) {
                  case Success(value) => complete(json)
                  case Failure(ex)    => complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")

                }
              }
          }
      } ~
      pathPrefix("transform" / Segment ) {
        field =>
          get {
            import JsonConversions._
            val result = askActor(s"/user/events/transforms",GetField(field)).mapTo[Option[Boolean]]
            onComplete(result) {
              case Success(Some(value)) => complete(s"Result: $value")
              case Success(None) => complete(s"Result: variable not available")
              case Failure(ex) => complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
            }
          }
      }


    }
  }
}

