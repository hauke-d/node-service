lazy val Http4sVersion = "0.20.0-M5"

lazy val DoobieVersion = "0.6.0"

lazy val FlywayVersion = "4.2.0"

lazy val CirceVersion = "0.9.3"

lazy val PureConfigVersion = "0.9.1"

lazy val LogbackVersion = "1.2.3"

lazy val ScalaTestVersion = "3.0.4"

lazy val ScalaMockVersion = "4.0.0"

lazy val root = (project in file("."))
  .settings(
    name := "node service",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.12.4",
    dockerExposedPorts += 8080,
    resolvers ++= Seq(
      "bintray-sbt-plugin-releases" at "http://dl.bintray.com/content/sbt/sbt-plugin-releases",
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
    ),
    scalacOptions ++= Seq("-language:higherKinds", "-Ypartial-unification"),
    libraryDependencies ++= Seq(
      "org.http4s"            %% "http4s-blaze-server"  % Http4sVersion,
      "org.http4s"            %% "http4s-circe"         % Http4sVersion,
      "org.http4s"            %% "http4s-dsl"           % Http4sVersion,

      "org.tpolecat"          %% "doobie-core"          % DoobieVersion,
      "org.tpolecat"          %% "doobie-postgres"      % DoobieVersion,
      "org.tpolecat"          %% "doobie-hikari"        % DoobieVersion,
      "org.tpolecat"          %% "doobie-scalatest"     % DoobieVersion % "test",

      "org.flywaydb"          %  "flyway-core"          % FlywayVersion,

      "io.circe"              %% "circe-generic"        % CirceVersion,

      "com.github.pureconfig" %% "pureconfig"           % PureConfigVersion,

      "ch.qos.logback"        %  "logback-classic"      % LogbackVersion,

      "org.scalatest"         %% "scalatest"            % ScalaTestVersion  % "test",
      "org.scalamock"         %% "scalamock"            % ScalaMockVersion  % "test"
    )
  )

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)