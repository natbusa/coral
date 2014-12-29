// Natalino Busa
// http://www.linkedin.com/in/natalinobusa

import sbt.Keys._
import sbt._

object TopLevelBuild extends Build {

  val appName = "coral"

  lazy val root = Project (
    id = appName,
    base = file ("."),
    settings = Settings.buildSettings ++ 
               Seq (
                 resolvers ++= Resolvers.allResolvers, 
                 libraryDependencies ++= Dependencies.allDependencies
               )
  ) dependsOn(macros)

  lazy val macros = Project(
    id = "macros",
    base = file("macros"),
    settings = Settings.buildSettings ++
      Seq (
        resolvers ++= Resolvers.allResolvers,
        libraryDependencies ++= Seq("org.json4s"         %% "json4s-jackson" % "3.2.11")
      )
    )

}

