// ============================================================
// build.sbt - Chat Backend
// Thêm dependencies dần theo hướng dẫn từng tuần:
//   Tuần 1: guice (đã có)
//   Tuần 2: jdbc, evolutions, postgresql
//   Tuần 3: mongodb-driver-reactivestreams, pekko-stream
//   Tuần 7: (ws đã có trong Play)
//   Tuần 8: ehcache
// ============================================================

name := """chat-backend"""
organization := "com.example"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.16"
javacOptions ++= Seq("--release", "21")

libraryDependencies ++= Seq(
  guice,  // Dependency Injection

  // ── Tuần 2: Database ────────────────────────────────────────
  // Uncomment khi đến Tuần 2
  // jdbc,
  // evolutions,
  // "org.postgresql" % "postgresql" % "42.7.4",

  // ── Tuần 3: MongoDB ─────────────────────────────────────────
  // Uncomment khi đến Tuần 3
  // "org.mongodb" % "mongodb-driver-reactivestreams" % "5.1.0",
  // "org.apache.pekko" %% "pekko-stream" % "1.1.2",

  // ── Tuần 8: Cache ───────────────────────────────────────────
  // Uncomment khi đến Tuần 8
  // ehcache,

  // Test
  "org.junit.jupiter" % "junit-jupiter-api" % "5.10.2" % Test,
  "org.junit.jupiter" % "junit-jupiter-engine" % "5.10.2" % Test,
)
