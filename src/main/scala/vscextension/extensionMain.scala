package vscextension

import scala.scalajs.js.annotation.JSExportTopLevel

import typings.vscode.mod as vscode

object extensionMain {

  /** The main entry for the extension, called when activated first time.
    */
  @JSExportTopLevel("activate") // Exports the function to javascript so that VSCode can load it
  def activate(context: vscode.ExtensionContext): Unit = {
    AgdaHighlighting.register(context)
    AgdaClient.activate(context)
  }
}
