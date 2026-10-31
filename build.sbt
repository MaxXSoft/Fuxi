// See README.md for license details.

ThisBuild / scalaVersion := "2.13.18"

name := "Fuxi"

version := "0.0.1"

val chiselVersion = "7.13.0"

libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion
libraryDependencies += "io.github.ucb-substrate" %% "cde" % "0.1.1"
libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0"
libraryDependencies += "org.scalatest" %% "scalatest-funsuite" % "3.2.20" % Test

Test / parallelExecution := false
Test / logBuffered := false

addCompilerPlugin(
  "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
)

scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-Xcheckinit",
  "-Ymacro-annotations",
)
