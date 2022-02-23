lazy val akkaHttpVersion = "10.2.7"
lazy val akkaVersion     = "2.6.18"

lazy val root = (project in file(".")).settings(
  inThisBuild(
    List(
      organization := "com.mmmedina",
      scalaVersion := "2.13.4"
    )
  ),
  name := "full-akka-app",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http"                % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json"     % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
    "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
    "ch.qos.logback"     % "logback-classic"          % "1.2.10",
    "org.scalatestplus" %% "mockito-3-4"              % "3.2.10.0"      % "test",
    "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
    "org.scalatest"     %% "scalatest"                % "3.2.9"         % Test,
    "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence"         % "2.6.18"
  )
)

addCommandAlias("f", "scalafmt")
addCommandAlias("fc", "scalafmtCheck")
addCommandAlias("tf", "test:scalafmt")
addCommandAlias("tfc", "test:scalafmtCheck")
addCommandAlias("fmt", ";f;tf")
