import scala.sys.process.*
import org.scalajs.linker.interface.{ModuleKind, ModuleInitializer, ModuleSplitStyle}

val outdir = "out" // output directory for the extension
val scala3Version = "3.3.9"
val munitVersion = "1.0.4"
// open command in sbt
lazy val open = taskKey[Unit]("open vscode")
lazy val prepareExtension = taskKey[Unit]("prepare extension output")

lazy val root = project
  .in(file("."))
  .enablePlugins(
    ScalaJSPlugin,
    ScalaJSBundlerPlugin,
    ScalablyTypedConverterPlugin
  )
  .settings(
    moduleName := "vscextension",
    organization := "com.doofin",
    scalaVersion := scala3Version,
    // warn unused imports and vars
    scalacOptions ++= Seq(
      "-Wunused:all"
    ),
    Compile / fastOptJS / artifactPath := baseDirectory.value / "out" / "extension.js",
    Compile / fullOptJS / artifactPath := baseDirectory.value / "out" / "extension.js",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    Compile / npmDependencies ++=
      Seq(
        "@types/vscode" -> "1.96.0", //
        "@types/node" -> "16.11.7", // ts 3.7
        "@types/node-fetch" -> "2.5.12", // ts 3.7,compile error for scalablytyped
        "@vscode/prompt-tsx" -> "0.3.0-alpha.18",
        "vscode-languageclient" -> "9.0.1" // working with manuallly created facade
      ),
    stIgnore ++= List(
    ),
    prepareExtension := prepareExtensionTask().value,
    open := openVSCodeTask().dependsOn(Compile / fastOptJS, prepareExtension).value
  )

lazy val integrationTest = project
  .in(file("src/integrationTest"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(root)
  .settings(
    moduleName := "agda-extension-integration-test",
    scalaVersion := scala3Version,
    Compile / scalaSource := baseDirectory.value / "main" / "scala",
    Compile / mainClass := Some("vscextension.IntegrationTestMain"),
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
    Compile / fastOptJS / artifactPath :=
      (root / baseDirectory).value / outdir / "test" / "extension.test.js"
  )

addCommandAlias("compile", ";fastOptJS")
addCommandAlias("dev", "~fastOptJS")
addCommandAlias("fix", ";scalafixEnable;scalafixAll;")

def openVSCodeTask(openVscode: Boolean = true): Def.Initialize[Task[Unit]] =
  Def
    .task[Unit] {
      val base = (ThisProject / baseDirectory).value
      val log = (ThisProject / streams).value.log

      val path = base.getCanonicalPath
      // launch vscode
      if (openVscode) {
        mLog("[opening] vscode")
        s"code --extensionDevelopmentPath=$path" ! log
      }
      ()
    }

def mLog(message: String): Unit = println(s"\u001b[33m[log] ${message}\u001b[0m")

def prepareExtensionTask(): Def.Initialize[Task[Unit]] =
  Def.task[Unit] {
    val base = (ThisProject / baseDirectory).value
    val log = (ThisProject / streams).value.log
    val output = base / outdir

    IO.createDirectory(output / "server")
    IO.createDirectory(output / "res")
    IO.createDirectory(output / "test")
    IO.copyFile(base / "package.json", output / "package.json")
    IO.copyDirectory(base / "src" / "res", output / "res")
    IO.copyFile(base / "server" / "server.js", output / "server" / "server.js")
    mLog(s"Copied server.js to output directory: ${output.getAbsolutePath}")

    def run(command: Seq[String]): Unit = {
      val exitCode = Process(command, base).!(log)
      if (exitCode != 0) sys.error(s"Command failed ($exitCode): ${command.mkString(" ")}")
    }

    run(Seq("npm", "pkg", "set", "main=./extension", "--prefix", output.getAbsolutePath))
    run(
      Seq(
        "npm",
        "install",
        "--prefix",
        output.getAbsolutePath,
        "--no-audit",
        "--no-fund"
      )
    )
  }
