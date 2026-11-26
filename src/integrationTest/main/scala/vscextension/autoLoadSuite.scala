package vscextension

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Thenable
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.Thenable.Implicits.*

import typings.node.nodeColonfsMod
import typings.vscode.mod as vscode
import typings.std.PromiseLike.PromiseLikeOps

import testUtils.*
import testUtils.implicitConv.*
import Mocha.*
import IntegrationTestMain.extensionId

object autoLoadSuite {
  private val source =
    """module AgdaAutoLoadIntegration where
      |data Nat : Set where
      |  zero : Nat
      |  suc : Nat → Nat
      |_+_ : Nat → Nat → Nat
      |zero + n = ?
      |suc n + m = {}
      |""".stripMargin

  def run(): Unit = {
    val extension = vscode.extensions.getExtension[js.Any](extensionId).get

    suite("Agda auto load") { () =>
      test("loads on open and after edits while holes remain") { () =>
        runAutoLoadTest(extension).toJSPromise
      }
    }
  }

  private def getDocument(extension: vscode.Extension[_]) = {
    val workspace = vscode.workspace.workspaceFolders.toOption
      .flatMap(_.headOption)
      .getOrElse(throw new Exception("The integration test requires an open workspace."))
    val uri = vscode.Uri.joinPath(workspace.uri, "AgdaAutoLoadIntegration.agda")
    val config = vscode.workspace.getConfiguration("agdaMode")

    nodeColonfsMod.writeFileSync(uri.fsPath, source)

    (
      config
        .update("connection.backend", "agda", vscode.ConfigurationTarget.Global)
        .toFuture
        .flatMap(_ => extension.activate())
        .flatMap(_ => vscode.workspace.openTextDocument(uri)),
      uri
    )
  }

  private def runAutoLoadTest(extension: vscode.Extension[_]): Future[Unit] = {
    val (documentF, uri) = getDocument(extension)

    documentF
      .flatMap { document =>
        vscode.window.showTextDocument(document).toFuture.map(_ => document)
      }
      .flatMap { document =>
        eventually(
          Future.successful(document.getText().replace("\r\n", "\n")),
          text =>
            text.contains("zero + n = {! !}") &&
              text.contains("suc n + m = {! !}") &&
              !document.isDirty,
          "automatic load to normalize and save ? and {} holes on open"
        ).flatMap { _ =>
          val appendedHole = "\nidentity : Nat → Nat\nidentity n = ?\n"
          val end = document.positionAt(document.getText().length)
          val edit = new vscode.WorkspaceEdit()
          edit.replace(document.uri, new vscode.Range(end, end), appendedHole)
          vscode.workspace.applyEdit(edit).toFuture
        }.flatMap { _ =>
          eventually(
            Future.successful(document.getText().replace("\r\n", "\n")),
            text => text.contains("identity n = {! !}") && !document.isDirty,
            "automatic load after adding a hole"
          )
        }.flatMap { _ =>
          val addedHole = document.getText().lastIndexOf("{! !}")
          if (addedHole < 0) throw new Exception("Automatic load did not normalize the added hole.")
          val position = document.positionAt(addedHole + 2)
          eventually(
            codeActionsAt(document, position),
            actions => actions.exists(_.command.get.command == "agda-mode.autoSimplified"),
            "goal actions after automatic reload"
          )
        }.map(_ => ())
      }
      .andThen { case _ =>
        vscode.commands.executeCommand[js.Any]("agda-mode.quit")
        nodeColonfsMod.unlinkSync(uri.fsPath)
      }
  }
}
