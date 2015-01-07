package com.natalinobusa.examples

import org.json4s.JsonAST.JValue

import scala.concurrent.duration._
import scala.util.{Failure, Success}

// akka
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._

// Actor messaging
import com.natalinobusa.examples.models.Messages._

// Spray
import spray.http.{HttpResponse, StatusCodes}
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

// terminology:
// in order not to clash which akka actors,
// we call the REST exposed actors (Beads internal name, external name Actor)
// we call the REST exposed api actor factory as coral, external name coral)
// we call the REST exposed actors connections "connections", exposed as "connections"

// declaraion time : /api/coral/flows/{flowid}/actors/{actorid}

trait ApiService extends HttpService {

  implicit def executionContext = actorRefFactory.dispatcher
  implicit val timeout = Timeout(1.seconds)

  def coralActor = actorRefFactory.actorSelection("/user/coral")

  // just a few handy shortcut
  def askActor(a: ActorPath, msg:Any) =  actorRefFactory.actorSelection(a).ask(msg)
  def askActor(a: String, msg:Any)    =  actorRefFactory.actorSelection(a).ask(msg)
  def askActor(a: ActorSelection, msg: Any) = a.ask(msg)

  val serviceRoute = {
    pathPrefix("api") {
      pathPrefix("bonds") {
        pathEnd {
          get {
            import JsonConversions._
            ctx => askActor(coralActor,ListBonds).mapTo[List[Long]]
              .onSuccess { case actors => ctx.complete(actors)}
          } ~
            post {
              import JsonConversions._
              entity(as[JObject]) { json =>
                ctx => askActor(coralActor,CreateBond(json)).mapTo[Boolean]
                  .onSuccess {
                  case true => ctx.complete(StatusCodes.Created, "ok")
                  case _ => ctx.complete("not created")
                }
              }
            } ~
            (delete | head | patch) {
              complete(HttpResponse(StatusCodes.MethodNotAllowed))
            }
        }
      } ~
      pathPrefix("actors") {
        pathEnd {
          get {
            import JsonConversions._
            ctx => askActor(coralActor,List).mapTo[List[Long]]
              .onSuccess { case actors => ctx.complete(actors)}
          } ~
          post {
            import JsonConversions._
            entity(as[JObject]) { json =>
            ctx => askActor(coralActor,CreateActor(json)).mapTo[Option[Long]]
              .onSuccess {
                case Some(id) => ctx.complete(id.toString)
                case _ => ctx.complete("not created")
              }
            }
          } ~
          (delete | head | patch) {
            complete(HttpResponse(StatusCodes.MethodNotAllowed))
          }
        }
      } ~
      pathPrefix("actors" / LongNumber) {
        actorId =>
        // find my actor
        onSuccess(askActor(coralActor, GetActorPath(actorId)).mapTo[Option[ActorPath]]) {
          actorPath => validate(actorPath.isDefined, "") {
            provide(actorPath.orNull) {
              ap => {
                pathPrefix("schema" ) {
                  get {
                    import JsonConversions._
                    val result = askActor(ap,ListFields).mapTo[JObject]
                    onComplete(result) {
                      case Success(json) => complete(json)
                      case Failure(ex)   => complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
                    }
                  }
                } ~
                  pathPrefix("state" / Segment ) {
                    field =>
                      get {
                        import JsonConversions._
                        val result = askActor(ap,GetField(field)).mapTo[JValue]
                        onComplete(result) {
                          case Success(json) => complete(json)
                          case Failure(ex)   => complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
                        }
                      }
                  } ~
                  // "in" should be exposed only as part of the input rest interface bead
                  // this should be moved to the actor itself, by passing the ctx around
                  // todo: create a REST bead and allow ctx to be passed there to continue processing
                  pathPrefix("in" ) {
                    post {
                      import JsonConversions._
                      entity(as[JObject]) { json =>
                        actorRefFactory.actorSelection(ap) ! json
                        complete(StatusCodes.Created, json)
                      }
                    }
                  }
              }
            }
          }
        }
      } ~
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

