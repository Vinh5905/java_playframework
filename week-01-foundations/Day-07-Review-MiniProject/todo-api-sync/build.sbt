name := """todo-api-sync"""
organization := "com.example"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.16"

libraryDependencies ++= Seq(
  guice,
  "org.junit.jupiter" % "junit-jupiter-api" % "5.10.2" % Test,
  "org.junit.jupiter" % "junit-jupiter-engine" % "5.10.2" % Test
)
