name := "Consense"
scalaVersion in ThisBuild := "2.13.3"
enablePlugins(JavaAppPackaging)

libraryDependencies += "org.scalatestplus" %% "mockito-3-4" % "3.2.2.0" % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.2" % Test
