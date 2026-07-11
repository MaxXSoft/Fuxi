// See README.md for license details.

ThisBuild / scalaVersion := "2.13.18"

name := "Fuxi"

version := "0.0.1"

val chiselVersion = "7.13.0"

libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion

addCompilerPlugin(
  "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
)

scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-Xcheckinit",
  "-Ymacro-annotations"
)
