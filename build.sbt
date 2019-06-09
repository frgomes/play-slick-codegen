scalaVersion in ThisBuild := "2.12.8"

val versions = new {
  val slick         = "3.3.1"
  val slf4j         = "1.7.26"
  val h2            = "1.4.199"
  val play = new {
    val slick       = "4.0.1"
  }
}
  

// shared sbt config between web project and codegen project
val sharedSettings: Seq[Setting[_]] =
  Seq(
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    scalacOptions ++= Seq(
      "-encoding", "UTF-8", // yes, this is 2 args
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-numeric-widen"),
    resolvers ++= Seq(
      "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
       Resolver.sonatypeRepo("releases"),
       Resolver.sonatypeRepo("snapshots")),
    libraryDependencies ++= Seq(
      "com.h2database" % "h2" % versions.h2,
      "org.slf4j" % "slf4j-nop" % versions.slf4j))

val twirlSettings: Seq[Setting[_]] =
  Seq(
    TwirlKeys.templateImports ++= Seq(
      "auto_generated._",
      "play.api.db.slick.Config.driver.simple._"
    )
  )

val playSettings: Seq[Setting[_]] =
  Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.play"  %% "play-slick"    % versions.play.slick))

val slickSettings: Seq[Setting[_]] =
  Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick"         % versions.slick))

val codegenSettings: Seq[Setting[_]] = slickSettings ++
  Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick-codegen" % versions.slick))


/** this is the root project */
lazy val root =
  (project in file("."))
    .aggregate(web, codegen)

/** web project containing main source code depending on slick and codegen project */
lazy val web =
  (project in file("web"))
    .settings(name := "computer-database-mde")
    .settings(sharedSettings: _*)
    .settings(playSettings: _*)
    .settings(slickSettings: _*)
    .settings(
        sourceGenerators in Compile += Def.task[Seq[File]] {
          val sourcesDir   = (scalaSource in Compile).value
          val generatedDir = (sourceManaged in Compile).value / "main" / "scala"
          val cacheDir     = streams.value.cacheDirectory / "SlickCodeGenerator"
          val dbURL        = "jdbc:h2:mem:test;INIT=runscript from 'conf/create.sql'" // connection info for a pre-populated throw-away, in-memory db for this demo, which is freshly initialized on every run
          val jdbcDriver   = "org.h2.Driver"
          val slickDriver  = "slick.driver.H2Driver"
          val pkgName      = "demo"
          val logger       = streams.value.log
          val cacheFunction =
            FileFunction.cached(cacheDir, inStyle = FilesInfo.hash, outStyle = FilesInfo.hash) {
              case files: Set[File] =>
                generateSlickModel(
                  classpath      = (managedClasspath in Compile).value.files,
                  runner         = (runner in Compile).value,
                  slickDriver    = slickDriver,
                  jdbcDriver     = jdbcDriver,
                  dbURL          = dbURL,
                  dstdir         = generatedDir,
                  pkgName        = pkgName,
                  logger         = logger).toSet }
          // apply cache function on generate Slick model
          cacheFunction(Set(sourcesDir)).toSeq
        },
    )
    .enablePlugins(PlayScala)
    .dependsOn(codegen)

/** codegen project containing the customized code generator */
lazy val codegen =
  (project in file("slick-codegen"))
    .settings(sharedSettings: _*)
    .settings(codegenSettings: _*)


def generateSlickModel(classpath: Seq[File],
                       runner: sbt.ScalaRun,
                       slickDriver: String,
                       jdbcDriver: String,
                       dbURL: String,
                       dstdir: File,
                       pkgName: String,
                       logger: sbt.util.Logger): Seq[File] = {
  IO.createDirectory(dstdir)
  runner.run("SlickCodeGenerator", classpath, Array(slickDriver, jdbcDriver, dbURL, dstdir.getAbsolutePath, pkgName), logger)
  Seq(new java.io.File(dstdir, s"${pkgName}/Tables.scala"))
}
