package com.natalinobusa.examples.models

import org.json4s.JObject

object Messages {

  // generic messages for resources
  case class  Get(id:Int)
  case class  Head(id:Int)
  case class  Delete(id:Int)
  case object Get
  case object List
  case object Create
  case object Delete

  // create accountActor
  case class  GetField(field:String)
  case object ListFields
}
