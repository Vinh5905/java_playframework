name := """url-shortener"""
organization := "com.example"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.16"
javacOptions ++= Seq("--release", "21")

libraryDependencies ++= Seq(
  guice,
  jdbc,
  evolutions,
  ws,

  // PostgreSQL driver
  "org.postgresql" % "postgresql" % "42.7.4",

  // JWT
  "com.auth0" % "java-jwt" % "4.4.0",

  // Password hashing
  "org.mindrot" % "jbcrypt" % "0.4",

  // Test
  "org.junit.jupiter" % "junit-jupiter-api" % "5.10.2" % Test,
  "org.junit.jupiter" % "junit-jupiter-engine" % "5.10.2" % Test,
  "org.mockito" % "mockito-core" % "5.11.0" % Test,
  "com.h2database" % "h2" % "2.2.224" % Test
)
