import Dependencies._

lazy val root = project
  .in(file("."))
  .settings(
    name := "sbt-mac-watch-service",
    description := "MacOSXWatchServicePlugin provides a WatchService that replaces " +
      "the default PollingWatchService on Mac OSX.",
    sbtPlugin := true,
    publishMavenStyle := false,
    scalaVersion := "2.12.4",
    organization := "com.swoval",
    version := "1.0.4-SNAPSHOT",
    libraryDependencies ++= Seq(jna, sbtIO),
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    bintrayRepository := "sbt-plugins",
    bintrayOrganization := Some("swoval"),
  )
