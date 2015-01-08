scalaVersion := "2.10.4"

resolvers ++= Seq(
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
	"Spray Repository"    at "http://repo.spray.io"
)

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-M1")