name := """hello-play"""
organization := "com.example"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.16"
javacOptions ++= Seq("--release", "21")

libraryDependencies += guice
libraryDependencies += "org.junit.jupiter" % "junit-jupiter-api" % "5.10.2" % Test
libraryDependencies += "org.junit.jupiter" % "junit-jupiter-engine" % "5.10.2" % Test
