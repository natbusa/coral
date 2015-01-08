// Natalino Busa
// http://www.linkedin.com/in/natalinobusa

import sbt.Keys._
import sbt._

import com.typesafe.sbt.SbtNativePackager.autoImport._

object Packaging {
  import com.typesafe.sbt.SbtNativePackager._
  import NativePackagerKeys._

  val packagingSettings = Seq(
    name := Settings.appName,
    NativePackagerKeys.packageName := "natalinobusa"
  ) ++ Seq(packageArchetype.java_application:_*) ++ buildSettings

 // val packagingSettings = Seq(packageArchetype.java_application:_*) ++ buildSettings
}

object TopLevelBuild extends Build {

  lazy val root = Project (
    id = Settings.appName,
    base = file ("."),
    settings = Settings.buildSettings ++
               Packaging.packagingSettings ++
               Seq (
                 resolvers ++= Resolvers.allResolvers, 
                 libraryDependencies ++= Dependencies.allDependencies
               )
  ) dependsOn(macros)

  lazy val macros = Project(
    id = "macros",
    base = file("macros"),
    settings = Settings.buildSettings ++ Seq(
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
      libraryDependencies ++= Seq("org.json4s" %% "json4s-jackson" % "3.2.11"),
      resolvers ++= Resolvers.allResolvers
    )
  )
}

