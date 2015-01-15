package com.natalinobusa.coral.app

import akka.actor.{ ActorSystem, Props }
import akka.io.IO
import spray.can.Http

object Boot extends App {
  implicit val system = ActorSystem()

  // create and start our service actor
  val service = system.actorOf(Props[ApiServiceActor], "api")
  
  // start a new HTTP server with our service actor as the handler
  IO(Http) ! Http.Bind(service, "0.0.0.0", port = 8888)

}
