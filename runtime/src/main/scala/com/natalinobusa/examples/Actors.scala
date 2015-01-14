package com.natalinobusa.examples

// scala

import scala.collection.immutable.{HashMap, SortedSet, SortedMap}
import scala.concurrent.Future
import scala.concurrent.duration._

// akka
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// scalaz monad transformers
import scalaz.{OptionT, Monad}
import scalaz.OptionT._

// First attempt to describe a user DSL for collecting values
import scala.reflect.{ClassTag, Manifest}

// Inter-actor messaging
import com.natalinobusa.examples.models.Messages._

//// Static code generation
import com.natalinobusa.macros.expose

class RuntimeActor extends Actor with ActorLogging {

  def actorRefFactory = context

  var actors = SortedMap.empty[Long, ActorPath]
  var count = 0L

  def receive = {
    case CreateActor(json) =>

      // for now just post a zscoreActor
      // todo: how about a factory? how about json to class?
      // a bit ugly, but it will do for now
      implicit val formats = org.json4s.DefaultFormats
      val actorProps = for {
        actorType <- (json \ "type").extractOpt[String]

        props <- actorType match {
          case "zscore"     => ZscoreActor(json)
          case "histogram"  => GroupByActor[HistogramActor](json)
          case "rest"       => RestActor(json)
        }
      } yield props

    val actorId = actorProps map { p =>
        count += 1
        val id = count
        val actor = actorRefFactory.actorOf(p, s"$id")
        actors += (id -> actor.path)
        id
      }

      sender ! actorId

    case ListActors =>
          sender ! actors.keys.toList
    //
    //    case Delete(id) =>
    //      directory.get(id).map(e => actorRefFactory.actorSelection(e._1) ! PoisonPill)
    //      directory -= id
    //      sender ! true
    //
    //    case  Get(id) =>
    //      val resource = directory.get(id).map( e => e._2 )
    //      log.info(s"streams get stream id $id, resource ${resource.toString} ")
    //      sender ! resource
    //
    case  GetActorPath(id) =>
      val path = actors.get(id)
      log.info(s"streams get stream id $id, path ${path.toString} ")
      sender ! path
  }
}

// metrics actor example
trait CoralActor extends Actor with ActorLogging {

  // begin: implicits and general actor init
  def actorRefFactory = context

  // transmit actor list
  var recipients = SortedSet.empty[ActorRef]
  var trigger: Option[String] = None           // numeric id  or None or "external"
  var collect = Map.empty[String, String]  // zero or more alias to actorpath id

  implicit def executionContext = actorRefFactory.dispatcher

  implicit val timeout = Timeout(10.milliseconds)

  def  askActor(a: String, msg: Any) = actorRefFactory.actorSelection(a).ask(msg)
  def tellActor(a: String, msg: Any) = actorRefFactory.actorSelection(a).!(msg)

  implicit val formats = org.json4s.DefaultFormats

  implicit val futureMonad = new Monad[Future] {
    def point[A](a: => A): Future[A] = Future.successful(a)

    def bind[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa flatMap f
  }

  // update properties (for now just fix trigger and collect lists)

  // Future[Option[A]] to Option[Future, A] using the OptionT monad transformer
  def getCollectInputField[A](actorAlias: String, subpath:String, field: String)(implicit mf: Manifest[A]) = {
    val result = collect.get(actorAlias) match {
      case Some(actorPath) =>
        val path = if (subpath == "") actorPath else s"$actorPath/$subpath"
        askActor(path, GetField(field)).mapTo[JValue].map(json => (json \ field).extractOpt[A])
      case None    => Future.failed(throw new Exception(s"Collect actor not defined"))
    }
    optionT(result)
  }

  def getTriggerInputField[A](jsonValue: JValue)(implicit mf: Manifest[A]) = {
    val value = Future.successful(jsonValue.extractOpt[A])
    optionT(value)
  }

  // todo: ideas
  // can you fold this with become/unbecome and translate into a tell pattern rather the an ask pattern?

  def process: JObject => OptionT[Future, Unit]

  def noProcess(json: JObject): OptionT[Future, Unit] = {
    OptionT.some(Future.successful({}))
  }

  def emit: JObject => JValue
  val doNotEmit: JObject => JValue       = _ => JNothing
  val passThroughEmit: JObject => JValue = json => json

  def transmitAdmin: Receive = {
    case RegisterActor(r) =>
      log.warning(s"registering ${r.path.toString}")
      recipients += r
  }

  // narrocast the result of the emit function to the recipient list
  def transmit: JValue => Unit = {
     json => json match {
       case v:JObject =>
         recipients map (actorRef => actorRef ! v)
       case _ =>
     }
  }

  def propertiesHandling: Receive = {
    case UpdateProperties(json) =>
      // update trigger
      trigger = (json \ "input" \ "trigger" \ "in" \ "type").extractOpt[String]
      trigger.getOrElse("none") match {
        case "none"     =>
        case "external" =>
        case "actor"    =>
          val source = (json \ "input" \ "trigger" \ "in" \ "source").extractOpt[String]
          source map { v =>
            log.warning(s"$v")
            tellActor(s"/user/coral/$v", RegisterActor(self))
          }

        case _    =>
      }

      // update collectlist
      // ugliest code ever :( not my best day
      val collectAliases = (json \ "input" \ "collect").extractOpt[Map[String,Any]]
      collect = collectAliases match {
        case Some(v) => {
          val x = v.keySet.map(k => (k, (json \ "input" \ "collect" \ k \ "source").extractOpt[Int].map(v => s"/user/coral/$v")))
          x.filter(_._2.isDefined).map(i => (i._1, i._2.get)).toMap
        }
        case None => Map()
      }
      log.warning(collect.toString())


      sender ! true

    case GetProperties =>
      sender ! JNothing
  }

  def jsonData: Receive = {
    // triggers (sensitivity list)
    case json: JObject =>
      val stage = process(json)

      // set the stage in motion ...
      val r = stage.run

      r.onSuccess {
        case Some(_) => transmit(emit(json))
        case None => log.warning("some variables are not available")
      }

      r.onFailure {
        case _ => log.warning("oh no, timeout or other serious exceptions!")
      }

  }

  def receive = jsonData orElse transmitAdmin orElse propertiesHandling orElse stateModel

  // everything is json
  // everything is described via json schema

  def stateModel:Receive

  // todo: extend the macro for an empty expose list
  // e.g def stateModel = expose()
  val emptyJsonSchema = parse("""{"title":"json schema", "type":"object"}""")
  def notExposed:Receive = {
    case ListFields =>
      sender ! emptyJsonSchema

    case GetField(_) =>
      sender ! JNothing
  }
}

//an actor with state
class HistogramActor extends CoralActor {

  // user defined state
  // todo: the given state should be persisted

  var count    = 0L
  var avg      = 0.0
  var sd       = 0.0
  var `var`    = 0.0

  def stateModel = expose(
    ("count", "integer", count),
    ("avg",   "number",  avg),
    ("sd",    "number",  Math.sqrt(`var`) ),
    ("var",   "number",  `var`)
  )

  // private variables not exposed
  var avg_1    = 0.0

  def process = {
    json: JObject =>
      for {
      // from trigger data
        value <- getTriggerInputField[Double](json \ "amount")
      } yield {
        // compute (local variables & update state)
        count match {
          case 0 =>
            count = 1
            avg_1 = value
            avg = value
          case _ =>
            count += 1
            avg_1 = avg
            avg = avg_1 * (count - 1) / count + value / count
        }

        // descriptive variance
        count match {
          case 0 =>
            `var` = 0.0
          case 1 =>
            `var` = (value - avg_1) * (value - avg_1)
          case _ =>
            `var` = ((count - 2) * `var` + (count - 1) * (avg - avg) * (avg_1 - avg) + (value - avg) * (value - avg)) / (count - 1)
        }
      }
  }

  def emit = doNotEmit
}

//todo: groupby actor should forward the bead methods to the children
class GroupByActor[T <: Actor](by: String)(implicit m: ClassTag[T]) extends CoralActor with ActorLogging {

  def stateModel = expose()
  def emit       = doNotEmit

  def process = {
    // todo: group_by support more than a level into dynamic actors tree
    json: JObject =>
      for {
        value   <- getTriggerInputField[String](json \ by)
      } yield {
        // create if it does not exist
        actorRefFactory.child(value) match
        {
          case Some(actorRef) => actorRef
          case None           => actorRefFactory.actorOf(Props[T], value)
        }
      } ! json
  }
}

object GroupByActor {
  def apply[T <: Actor](by: String)(implicit m: ClassTag[T]): Props = Props(new GroupByActor[T](by))
  def apply[T <: Actor](json:JObject)(implicit m: ClassTag[T]):Option[Props] = {
    implicit val formats = org.json4s.DefaultFormats
    for {
    // from trigger data
      by <- (json \ "params" \ "by").extractOpt[String]
    } yield {
      apply[T](by)
    }// todo: take better care of exceptions and error handling
  }
}

object RestActor {
  def apply(json:JObject) = Some(Props(new RestActor))
}

// metrics actor example
class RestActor extends CoralActor {
  def stateModel = expose()
  def process    = noProcess
  def emit       = passThroughEmit
}

object ZscoreActor {
  def apply(by:String, field: String, score:Double): Props = Props(new ZscoreActor(by,field, score))

  //declare actors params via json
  def apply(json:JObject):Option[Props] = {
    implicit val formats = org.json4s.DefaultFormats
    for {
      // from trigger data
        by <- (json \ "params" \ "by").extractOpt[String]
        field <- (json \ "params" \ "field").extractOpt[String]
        score <- (json \ "params" \ "score").extractOpt[Double]
      } yield {
        apply(by, field, score)
      }// todo: take better care of exceptions and error handling
  }
}

// metrics actor example
class ZscoreActor(by:String, field: String, score:Double) extends CoralActor {

  var outlier: Boolean = false

  def stateModel = expose( ("outlier", "integer", outlier) )

  def process = {
    json: JObject =>
      for {
        // from trigger data
        subpath <- getTriggerInputField[String](json \ by)
        value   <- getTriggerInputField[Double](json \ field)

        // from other actors
        avg     <- getCollectInputField[Double]( "histogram", subpath, "avg")
        std     <- getCollectInputField[Double]( "histogram", subpath, "sd")

        //alternative syntax from other actors multiple fields
        //(avg,std) <- getActorField[Double](s"/user/events/histogram/$city", List("avg", "sd"))
      } yield {
        // compute (local variables & update state)
        val th = avg + score * std
        outlier = value > th
      }
  }

  def emit =
  {
    json: JObject =>

      outlier match {
        case true =>
          // produce emit my results (dataflow)
          // need to define some json schema, maybe that would help
          val result = ("outlier" -> outlier)

          // what about merging with input data?
          val js = render(result) merge json

          //logs the outlier
          log.warning(compact(js))

          //emit resulting json
          js

        case _ => JNothing
      }
  }

}


