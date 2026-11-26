package vscextension.vsc

import scala.scalajs.js

import typings.vscode.mod as vscode
import vscextension.AgdaClient
import vscextension.facade.vscodeUtils.*

/** Commands are actions that a user can invoke in the vscode extension with command palette (ctrl+shift+p).
  *
  * This object registers all the commands in the extension.
  */
object commands {
  def registerAllCommands(context: vscode.ExtensionContext, agdaClient: AgdaClient): Unit = {
    val cmds: Seq[(String, Any => Unit)] = Seq(
      ("agda-mode.connect", (_: Any) => agdaClient.connectCommand(context)),
      ("agda-mode.load", (_: Any) => agdaClient.execAgdaCommand(context)),
      ("agda-mode.compile", (_: Any) => agdaClient.execAgdaCommand(context, compile = true)),
      ("agda-mode.quit", (_: Any) => agdaClient.quitCommand()),
      ("agda-mode.caseSplit", (_: Any) => agdaClient.runGoalAction("Case split")),
      ("agda-mode.give", (_: Any) => agdaClient.runGoalAction("Give")),
      (
        "agda-mode.autoSimplified",
        (_: Any) => agdaClient.runGoalAction("Auto (Simplified)", promptWhenEmpty = false)
      )
    )

    cmds.foreach { case (name, handler) =>
      context.pushDisposable(
        vscode.commands.registerCommand(name, handler)
      )
    }
  }
}
