import com.typesafe.sbt.packager.docker.DockerChmodType
import play.sbt.PlayRunHook

import scala.sys.process.Process

name := "Consense"

scalaVersion := "2.13.3"
javacOptions ++= Seq(
  "-source", "1.8",
  "-target", "1.8"
)
scalacOptions ++= Seq(
  "-target:jvm-1.8",
  "-language:postfixOps"
)

enablePlugins(PlayScala, DockerPlugin, UniversalPlugin)
disablePlugins(PlayLogback)

PlayKeys.playDefaultPort := 8080
mainClass in assembly := Some("bootstrap.Consense")
mainClass in Compile := Some("bootstrap.Consense")

lazy val uiSrcDir = settingKey[File]("Location of UI project")
lazy val uiBuildDir = settingKey[File]("Location of UI build's managed resources")
lazy val uiDepsDir = settingKey[File]("Location of UI build dependencies")

lazy val uiClean = taskKey[Unit]("Clean UI build files")
lazy val uiTest = taskKey[Unit]("Run UI tests when testing application.")
lazy val uiStage = taskKey[Unit]("Run UI build when packaging the application.")

uiSrcDir := baseDirectory.value / "ui"
uiBuildDir := uiSrcDir.value / "build"
uiDepsDir := uiSrcDir.value / "node_modules"

uiClean := {
  IO.delete(uiBuildDir.value)
}

uiTest := {
  val dir = uiSrcDir.value
  if (!(uiDepsDir.value.exists() || runProcess("yarn install", dir)) || !runProcess("yarn run test", dir)) {
    throw new Exception("UI tests failed.")
  }
}

uiStage := {
  val dir = uiSrcDir.value
  if (!runProcess("yarn install", dir) || !runProcess("yarn run build", dir)) {
    throw new Exception("UI build failed.")
  }
}

def runProcess(script: String, dir: File): Boolean = {
  if (System.getProperty("os.name").toLowerCase().contains("win")) {
    Process("cmd /c set CI=true&&" + script, dir)
  } else {
    Process("env CI=true " + script, dir)
  }
}.! == 0

def uiBuildHook(uiSrc: File): PlayRunHook = {

  new PlayRunHook {

    var process: Option[Process] = None

    var install: String = "yarn install"
    var run: String = "yarn run start"

    if (System.getProperty("os.name").toLowerCase().contains("win")) {
      install = "cmd /c" + install
      run = "cmd /c" + run
    }

    override def beforeStarted(): Unit = {
      Process(install, uiSrc).!
    }

    override def afterStarted(): Unit = {
      process = Some(
        Process(run, uiSrc).run
      )
    }

    override def afterStopped(): Unit = {
      process.foreach(p => p.destroy())
      process = None
    }

  }
}

packageName := "consense"

assembly := (assembly dependsOn uiStage).value
dist := (dist dependsOn uiStage).value
test := ((test in Test) dependsOn uiTest).value
clean := (clean dependsOn uiClean).value
publishLocal in Docker := (publishLocal in Docker).dependsOn(uiStage).value
publish in Docker := (publish in Docker).dependsOn(uiStage).value
PlayKeys.playRunHooks += uiBuildHook(uiSrcDir.value)
unmanagedResourceDirectories in Assets += uiBuildDir.value
unmanagedResourceDirectories in Compile += uiBuildDir.value

dockerUpdateLatest := true
dockerExposedPorts += 8080
dockerChmodType := DockerChmodType.UserGroupWriteExecute
dockerBaseImage := "openjdk:14-jdk"

val jacksonVersion = "2.10.0.pr2"
val log4j2Version = "2.11.1"
val log4jScalaVersion = "12.0"

libraryDependencies ++= Seq(
  guice,
  "org.apache.logging.log4j" % "log4j-api" % log4j2Version,
  "org.apache.logging.log4j" % "log4j-core" % log4j2Version,
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,
  "org.apache.logging.log4j" % "log4j-jul" % log4j2Version,
  "org.apache.logging.log4j" %% "log4j-api-scala" % log4jScalaVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "org.scalatest" %% "scalatest" % "3.2.2" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.2.0" % Test
)

excludeDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic",
  "ch.qos.logback" % "logback-core"
)