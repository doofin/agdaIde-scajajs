package vscextension.vsc

import scala.scalajs.js.annotation.JSExportTopLevel

import typings.vscode.mod as vscode

import _root_.vscextension.{AgdaClient, AgdaHighlighting}

object extensionMain {

  /** The main entry for the extension, called when activated first time.
    */
  @JSExportTopLevel("activate") // Exports the function to javascript so that VSCode can load it
  def activate(context: vscode.ExtensionContext): Unit = {
    val agdaHighlighting = AgdaHighlighting()
    val agdaClient = AgdaClient(agdaHighlighting)
    agdaHighlighting.register(context)
    agdaClient.activate(context)
    commands.registerAllCommands(context, agdaClient)
    CodeActions.registerCodeActions(context, agdaHighlighting)
  }
}
