resolvers in ThisBuild ++= Seq(
    "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
    Resolver.mavenLocal
)
//sbt new tillrohrmann/flink-project.g8
name := "flink-seedling-processor"

version := "0.1-SNAPSHOT"

organization := "org.tensorpol"

scalaVersion in ThisBuild := "2.11.7"

val flinkVersion = "1.4.2"

val flinkDependencies = Seq(
  "org.apache.flink" %% "flink-scala" % flinkVersion % "provided",
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
  "org.apache.flink" %% "flink-hadoop-compatibility" % flinkVersion % "provided",
  "org.apache.flink" %% "flink-connector-kafka-0.11" % flinkVersion,
  "org.apache.logging.log4j" % "log4j" % "2.11.0",
  "org.apache.logging.log4j" % "log4j-api" % "2.11.0",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.11.0",
  "org.apache.kafka" % "kafka-clients" % "0.11.0.2"
)

// https://mvnrepository.com/artifact/org.tensorflow/tensorflow
libraryDependencies += "org.tensorflow" % "tensorflow" % "1.4.0"
libraryDependencies += "org.bytedeco" % "javacv-platform" % "1.4.1"

lazy val root = (project in file(".")).
  settings(
    libraryDependencies ++= flinkDependencies
  )

mainClass in assembly := Some("org.tensorpol.imgproc.ImageClassifyProcessor")

// make run command include the provided dependencies
run in Compile := Defaults.runTask(fullClasspath in Compile,
                                   mainClass in (Compile, run),
                                   runner in (Compile,run)
                                  ).evaluated

// exclude Scala library from assembly
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)
