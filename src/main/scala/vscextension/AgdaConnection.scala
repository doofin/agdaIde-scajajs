package vscextension

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import typings.vscode.mod as vscode

import facade.vscodeUtils.*

object AgdaConnection {
  @js.native
  @JSImport("vscode-languageclient/node", "LanguageClient")
  private class AlsLanguageClient(
      id: String,
      name: String,
      serverOptions: js.Any,
      clientOptions: js.Any
  ) extends js.Object {
    def start(): js.Promise[Unit] = js.native
    def stop(): js.Promise[Unit] = js.native
    def sendRequest(method: String, params: js.Any): js.Promise[js.Dynamic] = js.native
  }

  private var connecting = false
  private var connected = false
  private var stopConnection: () => Unit = () => ()

  private def setting(name: String, default: String): String =
    vscode.workspace
      .getConfiguration("agdaMode")
      .asInstanceOf[js.Dynamic]
      .applyDynamic("get")(name, default)
      .asInstanceOf[String]

  private def isAgda(document: vscode.TextDocument): Boolean =
    document.languageId == "agda"

  private def workspacePath(document: vscode.TextDocument): String =
    vscode.workspace
      .getWorkspaceFolder(document.uri)
      .map(_.uri.fsPath)
      .getOrElse(".")

  private def reportFailure(error: Any): Unit = {
    connecting = false
    connected = false
    val message = s"Could not connect to Agda: $error"
    js.Dynamic.global.console.error(message)
    stopConnection()
    vscode.window.showErrorMessage(message)
  }

  private def connectAls(
      context: vscode.ExtensionContext,
      document: vscode.TextDocument,
      executable: String
  ): js.Promise[Unit] = {
    val output = vscode.window.createOutputChannel("Agda")
    val command = js.Dynamic.literal(
      command = executable,
      args = js.Array[String](),
      options = js.Dynamic.literal(cwd = workspacePath(document))
    )
    val serverOptions = js.Dynamic.literal(run = command, debug = command)
    val selector = js.Array(
      js.Dynamic.literal(scheme = "file", language = "agda")
    )
    val clientOptions = js.Dynamic.literal(
      documentSelector = selector,
      outputChannel = output,
      initializationOptions = js.Dynamic.literal(commandLineOptions = js.Array[String]())
    )
    val client = new AlsLanguageClient(
      "agda",
      "Agda Language Server",
      serverOptions,
      clientOptions
    )

    stopConnection = () => {
      client.stop()
      output.dispose()
      ()
    }
    context.pushDisposable(new vscode.Disposable(() => stopConnection()))

    client.start().`then`((_: Unit) => {
      output.appendLine(s"Starting Agda Language Server: $executable")
      client
        .sendRequest("agda", js.Dynamic.literal(tag = "CmdReqSYN"))
        .`then`((response: js.Dynamic) => {
          val tag = response.selectDynamic("tag").asInstanceOf[String]
          if (tag == "CmdResACK") {
            output.appendLine(s"Agda Language Server initialized: $response")
            vscode.window.showInformationMessage("Connected to Agda Language Server.")
            connected = true
            js.Promise.resolve(())
          } else {
            reportFailure(s"Unexpected Agda initialization response: $response")
            js.Promise.resolve(())
          }
        })
    })
  }

  private def connectAgda(
      context: vscode.ExtensionContext,
      document: vscode.TextDocument,
      executable: String
  ): js.Promise[Unit] =
    new js.Promise[Unit]((resolve, reject) => {
      val childProcess = js.Dynamic.global
        .require("child_process")
        .spawn(
          executable,
          js.Array("--interaction"),
          js.Dynamic.literal(
            cwd = workspacePath(document),
            stdio = js.Array("pipe", "pipe", "pipe")
          )
        )

      var buffer = ""
      var settled = false
      var ready = false
      var shuttingDown = false
      val timeout = js.Dynamic.global.setTimeout(
        (() => {
          if (!settled) {
            settled = true
            childProcess.kill()
            reject(new js.Error(s"Timed out waiting for Agda prompt from '$executable'."))
          }
        }): js.Function0[Unit],
        15000
      )

      stopConnection = () => {
        shuttingDown = true
        childProcess.kill()
        ()
      }
      context.pushDisposable(new vscode.Disposable(() => stopConnection()))

      val onOutput: js.Function1[js.Any, Unit] = (chunk: js.Any) => {
        val text = chunk.asInstanceOf[js.Dynamic].toString().asInstanceOf[String]
        buffer = (buffer + text).takeRight(10000)
        if (!settled && buffer.contains("Agda2>")) {
          settled = true
          ready = true
          connected = true
          js.Dynamic.global.clearTimeout(timeout)
          js.Dynamic.global.console.log(s"Connected to Agda: $executable")
          vscode.window.showInformationMessage("Connected to Agda.")
          resolve(())
        }
      }
      childProcess.selectDynamic("stdout").applyDynamic("on")("data", onOutput)
      childProcess.selectDynamic("stderr").applyDynamic("on")(
        "data",
        ((chunk: js.Any) => js.Dynamic.global.console.error(chunk)): js.Function1[js.Any, Unit]
      )
      childProcess.applyDynamic("on")(
        "error",
        ((error: js.Any) => {
          if (ready && !shuttingDown) {
            connected = false
            vscode.window.showErrorMessage(s"Agda process failed: $error")
          } else if (!settled) {
            settled = true
            js.Dynamic.global.clearTimeout(timeout)
            reject(error)
          }
        }): js.Function1[js.Any, Unit]
      )
      childProcess.applyDynamic("on")(
        "exit",
        ((code: js.Any, signal: js.Any) => {
          if (ready && !shuttingDown) {
            connected = false
            vscode.window.showErrorMessage(
              s"Agda stopped unexpectedly (code=$code, signal=$signal)."
            )
          } else if (!settled) {
            settled = true
            js.Dynamic.global.clearTimeout(timeout)
            reject(new js.Error(s"Agda exited before becoming ready (code=$code, signal=$signal)."))
          }
        }): js.Function2[js.Any, js.Any, Unit]
      )
    })

  private def connect(context: vscode.ExtensionContext, document: vscode.TextDocument): Unit = {
    if (!isAgda(document) || connecting || connected) return

    connecting = true
    val backend = setting("connection.backend", "agda")
    val result = backend match {
        case "agda" =>
          connectAgda(context, document, setting("connection.agdaPath", "agda"))
        case _ =>
          connectAls(context, document, setting("connection.alsPath", "als"))
      }

    result
      .`then`((_: Unit) => {
        connecting = false
        ()
      })
      .`catch`((error: Any) => {
        val pathSetting =
          if (backend == "agda") "agdaMode.connection.agdaPath"
          else "agdaMode.connection.alsPath"
        reportFailure(
          s"$error. Check that the executable is available to VS Code, or set $pathSetting to its absolute path."
        )
        ()
      })
  }

  def activate(context: vscode.ExtensionContext): Unit = {
    val onOpen = vscode.workspace.onDidOpenTextDocument((document: vscode.TextDocument) =>
      connect(context, document),
      js.undefined,
      js.undefined
    )
    context.pushDisposable(onOpen)
    context.pushDisposable(
      vscode.commands.registerCommand("agda-mode.connect", (_: Any) => {
        vscode.window.activeTextEditor.foreach(editor => connect(context, editor.document))
        ()
      })
    )

    vscode.window.activeTextEditor.foreach(editor => connect(context, editor.document))
  }
}
