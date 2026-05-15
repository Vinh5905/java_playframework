// Full project build.sbt - tất cả dependencies đã enabled
name := """chat-backend"""
organization := "com.example"
version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayJava, DockerPlugin)

scalaVersion := "2.13.16"
javacOptions ++= Seq("--release", "17")

libraryDependencies ++= Seq(
  guice,
  javaJdbc,
  evolutions,
  ws,
  "org.postgresql"  % "postgresql"                      % "42.7.4",
  "org.mongodb"     % "mongodb-driver-reactivestreams"   % "5.1.0",
  "org.apache.pekko" %% "pekko-stream"                  % "1.0.3",
  "com.auth0"       % "java-jwt"                        % "4.4.0",
  "org.junit.jupiter" % "junit-jupiter-api"             % "5.10.2" % Test,
  "org.junit.jupiter" % "junit-jupiter-engine"          % "5.10.2" % Test,
  "org.mockito"     % "mockito-core"                    % "5.11.0" % Test,
)

// Docker settings
dockerBaseImage := "eclipse-temurin:21-jre-alpine"
dockerExposedPorts := Seq(9000)
