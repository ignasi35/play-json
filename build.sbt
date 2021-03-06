/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

import interplay.ScalaVersions
import ReleaseTransformations._

import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.{
  mimaBinaryIssueFilters, mimaPreviousArtifacts
}

resolvers ++= DefaultOptions.resolvers(snapshot = true)

val specsVersion = "3.8.9"
val specsBuild = Seq(
  "specs2-core"
).map("org.specs2" %% _ % specsVersion)

val jacksonVersion = "2.8.8"
val jacksons = Seq(
  "com.fasterxml.jackson.core" % "jackson-core",
  "com.fasterxml.jackson.core" % "jackson-annotations",
  "com.fasterxml.jackson.core" % "jackson-databind",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"
).map(_ % jacksonVersion)

val joda = Seq(
  "joda-time" % "joda-time" % "2.9.9"
    //"org.joda" % "joda-convert" % "1.8.1")
)

def jsonDependencies(scalaVersion: String) = Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion
)

// Common settings
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._

val previousVersion = Def.setting[Option[String]] {
  scalaVersion.value.split('.')(1) match {
    case "10" => Some("2.6.0-M6")
    case "11" => Some("2.5.14")
    case "12" => Some("2.6.0-M1")
    case _ => None
  }
}

lazy val playJsonMimaSettings = mimaDefaultSettings ++ Seq(
  mimaPreviousArtifacts := previousVersion.value.map { v =>
    organization.value %% moduleName.value % v
  }.toSet
)

lazy val commonSettings = SbtScalariform.scalariformSettings ++ Seq(
    scalaVersion := ScalaVersions.scala212,
    crossScalaVersions := Seq(ScalaVersions.scala210, ScalaVersions.scala211, ScalaVersions.scala212),
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(SpacesAroundMultiImports, true)
      .setPreference(SpaceInsideParentheses, false)
      .setPreference(DanglingCloseParenthesis, Preserve)
      .setPreference(PreserveSpaceBeforeArguments, true)
      .setPreference(DoubleIndentClassDeclaration, true)
  )

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayRootProject, ScalaJSPlugin)
  .aggregate(
    `play-jsonJS`,
    `play-jsonJVM`,
    `play-functionalJS`,
    `play-functionalJVM`,
    `play-json-joda`
  ).settings(commonSettings: _*)

val isNew = implicitly[ProblemFilter](
  _.ref.isInstanceOf[ReversedMissingMethodProblem])

val filtersNew = Seq(
  // Macro/compile-time
  ProblemFilters.exclude[MissingClassProblem]("play.api.libs.json.JsMacroImpl$ImplicitResolver$2$ImplicitTransformer$"),
  ProblemFilters.exclude[MissingClassProblem]("play.api.libs.json.JsMacroImpl$ImplicitResolver$2$Implicit$"),
  ProblemFilters.exclude[MissingClassProblem]("play.api.libs.json.JsMacroImpl$ImplicitResolver$2$Implicit")
)

val compatFilters = {
  val validationFilter: ProblemFilter =
    !_.ref.toString.contains("validation.ValidationError")

  Seq(
    validationFilter,
    ProblemFilters.exclude[MissingClassProblem]("play.libs.Json"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.json.ConstraintReads.min"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.json.ConstraintReads.max"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.json.Reads.min"),
    ProblemFilters.exclude[IncompatibleMethTypeProblem]("play.api.libs.json.Reads.max"),
    ProblemFilters.exclude[UpdateForwarderBodyProblem]("play.api.libs.json.DefaultWrites.traversableWrites"),

    // Was deprecated
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.json.Json.toJson"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.json.Json.fromJson"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.json.JsError.toFlatJson"),
    ProblemFilters.exclude[DirectMissingMethodProblem]("play.api.libs.json.JsError.toFlatJson")
  )
}

lazy val `play-json` = crossProject.crossType(CrossType.Full)
  .in(file("play-json"))
  .enablePlugins(PlayLibrary, Playdoc)
  .settings(commonSettings)
  .settings(playJsonMimaSettings)
  .settings(
    mimaBinaryIssueFilters ++= {
      if (scalaVersion.value startsWith "2.11") {
        compatFilters ++ filtersNew :+ isNew
      } else Seq(isNew)
    },
    libraryDependencies ++= jsonDependencies(scalaVersion.value) ++ Seq(
      "org.scalatest" %%% "scalatest" % "3.0.3" % Test,
      "org.scalacheck" %%% "scalacheck" % "1.13.5" % Test,
      "com.chuusai" %% "shapeless" % "2.3.2" % Test,
      "org.typelevel" %% "macro-compat" % "1.1.1",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    ),
    sourceGenerators in Compile += Def.task{
      val dir = (sourceManaged in Compile).value

      val file = dir / "upickle" / "Generated.scala"
      val (writes, reads) = (1 to 22).map{ i =>
        def commaSeparated(s: Int => String) = (1 to i).map(s).mkString(", ")
        def newlineSeparated(s: Int => String) = (1 to i).map(s).mkString("\n")
        val writerTypes = commaSeparated(j => s"T$j: Writes")
        val readerTypes = commaSeparated(j => s"T$j: Reads")
        val typeTuple = commaSeparated(j => s"T$j")
        val written = commaSeparated(j => s"implicitly[Writes[T$j]].writes(x._$j)")
        val readValues = commaSeparated(j => s"t$j")
        val readGenerators = newlineSeparated(j => s"t$j <- implicitly[Reads[T$j]].reads(arr(${j-1}))")
        (s"""
          implicit def Tuple${i}W[$writerTypes]: Writes[Tuple${i}[$typeTuple]] = Writes[Tuple${i}[$typeTuple]](
            x => JsArray(Array($written))
          )
          """,s"""
          implicit def Tuple${i}R[$readerTypes]: Reads[Tuple${i}[$typeTuple]] = Reads[Tuple${i}[$typeTuple]]{
            case JsArray(arr) if arr.size == $i =>
              for{
                $readGenerators
              } yield Tuple$i($readValues)

            case _ =>
              JsError(Seq(JsPath() -> Seq(JsonValidationError("Expected array of $i elements"))))
          }
        """)
      }.unzip

      IO.write(file, s"""
          package play.api.libs.json

          trait GeneratedReads {
            ${reads.mkString("\n")}
          }

          trait GeneratedWrites{
            ${writes.mkString("\n")}
          }
        """)
      Seq(file)
    }.taskValue
  )
  .dependsOn(`play-functional`)

lazy val `play-json-joda` = project
  .in(file("play-json-joda"))
  .enablePlugins(PlayLibrary)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= joda ++ specsBuild.map(_ % Test)
  )
  .dependsOn(`play-jsonJVM`)

lazy val `play-jsonJVM` = `play-json`.jvm.
  settings(
    libraryDependencies ++=
      joda ++ // TODO: remove joda after 2.6.0
      jacksons ++ specsBuild.map(_ % Test) :+ (
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Test
    ),
    unmanagedSourceDirectories in Test ++= (baseDirectory.value / ".." / ".." / "docs" / "manual" / "working" / "scalaGuide" ** "code").get
  )

lazy val `play-jsonJS` = `play-json`.js

lazy val `play-functional` = crossProject.crossType(CrossType.Pure)
  .in(file("play-functional"))
  .settings(commonSettings)
  .settings(playJsonMimaSettings)
  .enablePlugins(PlayLibrary)

lazy val `play-functionalJVM` = `play-functional`.jvm
lazy val `play-functionalJS` = `play-functional`.js

import pl.project13.scala.sbt.JmhPlugin
lazy val benchmarks = project
  .in(file("benchmarks"))
  .enablePlugins(JmhPlugin, PlayNoPublish)
  .settings(commonSettings)
  .dependsOn(`play-jsonJVM`)

playBuildRepoName in ThisBuild := "play-json"

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
  pushChanges
)

lazy val checkCodeFormat = taskKey[Unit]("Check that code format is following Scalariform rules")

checkCodeFormat := {
  val exitCode = "git diff --exit-code".!
  if (exitCode != 0) {
    sys.error(
      """
        |ERROR: Scalariform check failed, see differences above.
        |To fix, format your sources using sbt scalariformFormat test:scalariformFormat before submitting a pull request.
        |Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request.
      """.stripMargin)
  }
}

addCommandAlias("validateCode", ";scalariformFormat;checkCodeFormat")
