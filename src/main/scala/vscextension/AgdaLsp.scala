package vscextension

import scala.scalajs.js

import typings.vscode.mod as vscode

import facade.vsLanguageClient.{DocumentFilter, LanguageClient, LanguageClientOptions, ServerOptions}
import facade.vscodeUtils.*

object AgdaLsp {
  private var activeClient: js.UndefOr[LanguageClient] = js.undefined
  private var outputChannel: js.UndefOr[vscode.OutputChannel] = js.undefined

  def isActive: Boolean = activeClient.isDefined

  def connect(
      context: vscode.ExtensionContext,
      cwd: String,
      executable: String,
      onConnected: () => Unit
  ): js.Promise[Unit] = {
    val output = vscode.window.createOutputChannel("Agda")
    val command = js.Dynamic.literal(
      command = executable,
      args = js.Array[String](),
      options = js.Dynamic.literal(cwd = cwd)
    )
    val serverOptions = ServerOptions(run = command, debug = command)
    val clientOptions = LanguageClientOptions(
      documentSelector = js.Array(DocumentFilter("file", "agda")),
      outputChannel = output,
      initializationOptions = js.Dynamic.literal(commandLineOptions = js.Array[String]())
    )
    val client = new LanguageClient(
      "agda",
      "Agda Language Server",
      serverOptions,
      clientOptions
    )

    activeClient = client
    outputChannel = output
    context.pushDisposable(new vscode.Disposable(() => stop()))
    context.pushDisposable(
      client.onRequest(
        "agda",
        (message: js.Dynamic) => {
          output.appendLine(s"ALS response: $message")
          js.Promise.resolve(null)
        }
      )
    )

    client
      .start()
      .`then`((_: Unit) => {
        output.appendLine(s"Starting Agda Language Server: $executable")
        client
          .sendRequest("agda", js.Dynamic.literal(tag = "CmdReqSYN"))
          .`then`((response: js.Dynamic) => {
            val tag = response.selectDynamic("tag").asInstanceOf[String]
            if (tag == "CmdResACK") {
              output.appendLine(s"Agda Language Server initialized: $response")
              vscode.window.showInformationMessage("Connected to Agda Language Server.")
              onConnected()
              js.Promise.resolve(())
            } else {
              new js.Promise[Unit]((_, reject) => reject(s"Unexpected Agda initialization response: $response"))
            }
          })
      })
  }

  def sendCommand(request: String, label: String): Unit =
    activeClient.foreach { client =>
      outputChannel.foreach(_.show(true))
      outputChannel.foreach(_.appendLine(s"> $request"))
      client
        .sendRequest(
          "agda",
          js.Dynamic.literal(tag = "CmdReq", contents = request)
        )
        .`then`((response: js.Dynamic) => {
          outputChannel.foreach(_.appendLine(s"ALS command accepted: $response"))
          val tag = response.selectDynamic("tag").asInstanceOf[String]
          val result = response.selectDynamic("contents").asInstanceOf[js.Any]
          if (tag == "CmdRes" && (js.isUndefined(result) || result == null)) {
            vscode.window.showInformationMessage(s"Agda $label completed successfully.")
          } else {
            val message =
              if (tag == "CmdRes") s"Agda $label failed. See the Agda Output channel for details."
              else s"Agda $label returned an unexpected response: $response"
            outputChannel.foreach(_.appendLine(message))
            vscode.window.showErrorMessage(message)
          }
          ()
        })
        .`catch`((error: Any) => {
          val message = s"Agda $label failed: $error"
          outputChannel.foreach(_.appendLine(message))
          vscode.window.showErrorMessage(message)
          ()
        })
    }

  def stop(): Unit = {
    activeClient.foreach(_.stop())
    outputChannel.foreach(_.dispose())
    activeClient = js.undefined
    outputChannel = js.undefined
  }
}
