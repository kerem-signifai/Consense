name := "Consense"
scalaVersion in ThisBuild := "2.13.3"
enablePlugins(JavaAppPackaging)

val jacksonVersion = "2.10.0.pr2"

libraryDependencies += "org.scalatestplus" %% "mockito-3-4" % "3.2.2.0" % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.2" % Test
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
libraryDependencies += "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion
