package vscextension.vsc

import scala.scalajs.js

import typings.vscode.mod as vscode

import vscextension.{AgdaClient, AgdaHighlighting}
import vscextension.facade.vscodeUtils.*

object CodeActions {
  private def action(title: String, command: String): vscode.CodeAction = {
    val result = new vscode.CodeAction(title, vscode.CodeActionKind.QuickFix)
    result.command = vscode.Command(command = command, title = title)
    result
  }

  private def emptyBracesAt(document: vscode.TextDocument, offset: Int): Option[(Int, Int)] = {
    val text = document.getText()
    var start = text.indexOf("{}")
    while (start >= 0) {
      if (start <= offset && offset <= start + 2) return Some((start, start + 2))
      start = text.indexOf("{}", start + 2)
    }
    None
  }

  private def holeSyntaxFix(
      document: vscode.TextDocument,
      start: Int,
      end: Int
  ): vscode.CodeAction = {
    val fix = new vscode.CodeAction(
      "Replace `{}` with an Agda hole `{! !}`",
      vscode.CodeActionKind.QuickFix
    )
    val edit = new vscode.WorkspaceEdit()
    edit.replace(
      document.uri,
      new vscode.Range(document.positionAt(start), document.positionAt(end)),
      "{! !}"
    )
    fix.edit = edit
    fix
  }

  def registerCodeActions(context: vscode.ExtensionContext): Unit = {
    context.pushDisposable(
      vscode.commands.registerCommand(
        "agda-mode.caseSplit",
        (_: Any) => AgdaClient.caseSplitAtCursor()
      )
    )
    context.pushDisposable(
      vscode.commands.registerCommand(
        "agda-mode.give",
        (_: Any) => AgdaClient.giveAtCursor()
      )
    )
    context.pushDisposable(
      vscode.commands.registerCommand(
        "agda-mode.autoSimplified",
        (_: Any) => AgdaClient.autoSimplifiedAtCursor()
      )
    )

    val provider = new js.Object {
      def provideCodeActions(
          document: vscode.TextDocument,
          range: vscode.Range,
          _context: vscode.CodeActionContext,
          _token: vscode.CancellationToken
      ): js.Array[vscode.CodeAction] = {
        val offset = document.offsetAt(range.start).toInt
        val goal = AgdaHighlighting.goalAt(document, offset)
        val canSplit = goal.exists { found =>
          val line = document.lineAt(document.positionAt(found.start).line.toInt)
          val lineEnd = document.offsetAt(line.range.end).toInt
          document.getText().substring(found.end, lineEnd).trim.isEmpty
        }
        if (document.languageId == "agda" && goal.isDefined) {
          val actions = Vector.newBuilder[vscode.CodeAction]
          if (canSplit) actions += action("Agda: Case split", "agda-mode.caseSplit")
          actions += action("Agda: Give (fill goal)", "agda-mode.give")
          actions += action("Agda: Auto (Simplified)", "agda-mode.autoSimplified")
          js.Array(actions.result()*)
        } else if (document.languageId == "agda") {
          emptyBracesAt(document, offset) match {
            case Some((start, end)) => js.Array(holeSyntaxFix(document, start, end))
            case None               => js.Array[vscode.CodeAction]()
          }
        } else js.Array[vscode.CodeAction]()
      }
    }.asInstanceOf[vscode.CodeActionProvider[vscode.CodeAction]]

    context.pushDisposable(
      vscode.languages.registerCodeActionsProvider(
        selector = "agda",
        provider = provider,
        metadata = vscode
          .CodeActionProviderMetadata()
          .setProvidedCodeActionKinds(js.Array(vscode.CodeActionKind.QuickFix))
      )
    )
  }
}
