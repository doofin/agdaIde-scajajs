package vscextension.vsc

import scala.scalajs.js.annotation.JSExportTopLevel

import typings.vscode.mod as vscode

import _root_.vscextension.{AgdaClient, AgdaHighlighting}

object extensionMain {

  /** The main entry for the extension, called when activated first time.
    */
  @JSExportTopLevel("activate") // Exports the function to javascript so that VSCode can load it
  def activate(context: vscode.ExtensionContext): Unit = {
    AgdaHighlighting.register(context)
    AgdaClient.activate(context)
    CodeActions.registerCodeActions(context)
  }
}
