import java.io.File
import java.nio.file.{ Files => JFiles, Path => JPath, StandardCopyOption }

import Dependencies._
import StandardCopyOption.REPLACE_EXISTING
import bintray.BintrayKeys.{
  bintrayOrganization,
  bintrayPackage,
  bintrayRepository,
  bintrayUnpublish
}
import bintray.BintrayPlugin
import ch.jodersky.sbt.jni.plugins.JniJavah.autoImport.javah
import ch.jodersky.sbt.jni.plugins.JniNative
import ch.jodersky.sbt.jni.plugins.JniNative.autoImport._
import ch.jodersky.sbt.jni.plugins.JniPackage.autoImport._
import com.typesafe.sbt.GitVersioning
import com.typesafe.sbt.SbtGit.git
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.toPlatformDepsGroupID
import org.scalajs.core.tools.linker.backend.ModuleKind
import org.scalajs.sbtplugin.JSPlatform
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{ scalaJSModuleKind, fastOptJS, fullOptJS }
import sbt.Keys._
import sbt._
import sbtcrossproject.CrossPlugin.autoImport._
import sbtcrossproject.CrossProject

import scala.collection.JavaConverters._
import scala.sys.process._
import scala.util.Properties
import scalajsbundler.BundlingMode
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.JSCrossProjectOps

object Build {
  def commonSettings: SettingsDefinition = Seq(
    scalaVersion := "2.12.4",
    resolvers += Resolver.sonatypeRepo("releases"),
    git.baseVersion := baseVersion,
    organization := "com.swoval",
    bintrayOrganization := Some("swoval"),
    licenses += ("Apache-2.0", url(
      "https:www.apache.org/licenses/LICENSE-2.0.html"
    ))
  )
  val projects: Seq[ProjectReference] =
    (if (Properties.isMac) Seq[ProjectReference](appleFileEvents.jvm, appleFileEvents.js, plugin)
     else Seq.empty) ++
      Seq[ProjectReference](
        testing.js,
        testing.jvm,
        files.jvm,
        files.js
      )

  lazy val root = project
    .in(file("."))
    .aggregate(projects: _*)
    .settings(
      publish := {},
      bintrayUnpublish := {}
    )

  lazy val appleFileEvents: CrossProject = crossProject(JSPlatform, JVMPlatform)
    .in(file("apple-file-events"))
    .configurePlatform(JVMPlatform)(_.enablePlugins(JniNative))
    .configurePlatform(JSPlatform)(_.enablePlugins(ScalaJSBundlerPlugin))
    .settings(
      commonSettings,
      name := "apple-file-events",
      bintrayPackage := "apple-file-events",
      bintrayRepository := "sbt-plugins",
      description := "JNI library for apple file system",
      sourceDirectory in nativeCompile := sourceDirectory.value / "main" / "native",
      publishMavenStyle := false,
      target in javah := sourceDirectory.value / "main" / "native" / "include",
      watchSources ++= sourceDirectory.value.globRecursive("*.hpp" | "*.cc").get,
      utestCrossTest,
      utestFramework
    )
    .jsSettings(
      webpackBundlingMode := BundlingMode.LibraryOnly(),
      useYarn := false,
      scalaJSModuleKind := ModuleKind.CommonJSModule,
      sourceGenerators in Compile += Def.task {
        val pkg = "com/swoval/files/apple"
        val target = (managedSourceDirectories in Compile).value.head.toPath
        val base = baseDirectory.value.toPath
        val javaSourceDir: JPath =
          base.relativize((javaSource in Compile).value.toPath.resolve(pkg))
        val javaDir: JPath = base.resolveSibling("jvm").resolve(javaSourceDir)
        val sources = Seq("Event", "Flags", "FileEvent")
        val javaSources = sources.map(f => javaDir.resolve(s"$f.java").toString)

        val clazz = "com.swoval.code.Converter"
        def cp =
          fullClasspath
            .in(scalagen, Runtime)
            .value
            .map(_.data)
            .mkString(File.pathSeparator)
        val cmd = Seq("java", "-classpath", cp, clazz) ++ javaSources :+ target.toString
        println(cmd.!!)
        sources.map(f => target.resolve(s"$f.scala").toFile)
      }.taskValue,
      cleanAllGlobals,
      nodeNativeLibs
    )

  def addLib(dir: File): File = {
    val target = dir.toPath.resolve("node_modules/lib")
    if (!JFiles.exists(target))
      JFiles.createSymbolicLink(target,
                                appleFileEvents.js.base.toPath.toAbsolutePath.resolve("npm/lib"))
    dir
  }
  def nodeNativeLibs: SettingsDefinition = Seq(
    (npmUpdate in Compile) := addLib((npmUpdate in Compile).value),
    (npmUpdate in Test) := addLib((npmUpdate in Test).value)
  )

  def cleanGlobals(file: Attributed[File]) = {
    val content = new String(JFiles.readAllBytes(file.data.toPath))
      .replaceAll("([ ])*[a-zA-Z$0-9.]+\\.___global.", "$1")
    JFiles.write(file.data.toPath, content.getBytes)
    file
  }
  def cleanAllGlobals: SettingsDefinition = Seq(
    (fastOptJS in Compile) := cleanGlobals((fastOptJS in Compile).value),
    (fastOptJS in Test) := cleanGlobals((fastOptJS in Test).value),
    (fullOptJS in Compile) := cleanGlobals((fullOptJS in Compile).value),
    (fullOptJS in Test) := cleanGlobals((fullOptJS in Test).value)
  )
  lazy val files: CrossProject = crossProject(JSPlatform, JVMPlatform)
    .in(file("files"))
    .enablePlugins(GitVersioning)
    .jsConfigure(_.enablePlugins(ScalaJSBundlerPlugin))
    .jsSettings(
      scalacOptions += "-P:scalajs:sjsDefinedByDefault",
      scalaJSModuleKind := ModuleKind.CommonJSModule,
      webpackBundlingMode := BundlingMode.LibraryOnly(),
      useYarn := false,
      libraryDependencies += "com.swoval" %%% "apple-file-events" % appleEventsVersion,
      cleanAllGlobals,
      nodeNativeLibs,
      (fullOptJS in Compile) := {
        val res = (fullOptJS in Compile).value
        JFiles.copy(res.data.toPath,
                    baseDirectory.value.toPath.resolve("npm/files.js"),
                    REPLACE_EXISTING)
        res
      },
      ioScalaJS
    )
    .settings(
      scalaVersion := "2.12.4",
      commonSettings,
      name := "file-utilities",
      bintrayPackage := "file-utilities",
      bintrayRepository := "sbt-plugins",
      description := "File system apis.",
      publishMavenStyle := false,
      libraryDependencies ++= Seq(
        zinc,
        scalaMacros % scalaVersion.value,
        "com.swoval" % "apple-file-events" % appleEventsVersion
      ),
      utestCrossTest,
      utestFramework
    )
    .dependsOn(testing % "test->test")

  lazy val plugin: Project = project
    .in(file("plugin"))
    .enablePlugins(GitVersioning, BintrayPlugin)
    .settings(
      commonSettings,
      name := "sbt-mac-watch-service",
      bintrayPackage := "sbt-mac-watch-service",
      bintrayRepository := "sbt-plugins",
      description := "MacOSXWatchServicePlugin provides a WatchService that replaces " +
        "the default PollingWatchService on Mac OSX.",
      publishMavenStyle := false,
      sbtPlugin := true,
      libraryDependencies ++= Seq(
        zinc % "provided",
        sbtIO % "provided",
        "com.lihaoyi" %% "utest" % utestVersion % "test",
        "com.swoval" % "apple-file-events" % appleEventsVersion % "provided"
      ),
      watchSources ++= (watchSources in files.jvm).value,
      utestFramework,
      resourceGenerators in Compile += Def.task {
        // This makes a fat jar containing all of the classes in appleFileEvents and files.
        lazy val apfscd = (classDirectory in Compile in appleFileEvents.jvm).value.toPath
        lazy val filescd = (classDirectory in Compile in files.jvm).value.toPath
        lazy val libs = (nativeLibraries in Compile in appleFileEvents.jvm).value
        (compile in Compile in appleFileEvents.jvm).value
        (compile in Compile in files.jvm).value
        lazy val resourcePath = (resourceManaged in Compile).value.toPath
        def copy(s: File, d: File) = { IO.copyFile(s, d); d }
        def projectFiles(p: JPath) = JFiles.walk(p).iterator.asScala.toSeq.collect {
          case f if f.toString endsWith ".class" =>
            copy(f.toFile, resourcePath.resolve(p.relativize(f)).toFile)
        }
        libs.map {
          case (l, p) if p startsWith File.separator =>
            copy(l, resourcePath.resolve(p.substring(1)).toFile)
          case (l, p) => copy(l, resourcePath.resolve(p).toFile)
        } ++ projectFiles(apfscd) ++ projectFiles(filescd)
      }.taskValue
    )
    .dependsOn(files.jvm % "provided->compile;test->test", testing.jvm % "test->test")

  lazy val scalagen: Project = project
    .in(file("scalagen"))
    .settings(
      publish := {},
      resolvers += Resolver.bintrayRepo("nightscape", "maven"),
      scalaVersion := "2.11.12",
      libraryDependencies += Dependencies.scalagen
    )

  lazy val testing: CrossProject = crossProject(JSPlatform, JVMPlatform)
    .in(file("testing"))
    .jsSettings(
      scalaJSModuleKind := ModuleKind.CommonJSModule,
      ioScalaJS
    )
    .settings(
      scalaVersion := "2.12.4",
      libraryDependencies += scalaMacros % scalaVersion.value,
      utestCrossMain,
      utestFramework
    )
}
