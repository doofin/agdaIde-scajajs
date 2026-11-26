package vscextension

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.Thenable
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.timers.setTimeout

import typings.node.childProcessMod.spawnSync
import typings.node.nodeColonfsMod
import typings.vscode.mod as vscode
import typings.std.PromiseLike.PromiseLikeOps

import testUtils.*
import testUtils.implicitConv.*
import Mocha.*
import IntegrationTestMain.extensionId
import vsc.commands

object agdaAutoSuite {
  def agdaVersion = {
    val result = spawnSync("agda", js.Array("--numeric-version"))
    val status = result.status
    if (status != null && status.asInstanceOf[Double] == 0.0) result.stdout.toString()
    else ""
  }

  def run(): Unit = {
    val extension = vscode.extensions.getExtension[js.Any](extensionId).get

    suite("Agda extension host") { () =>
      test("activates and registers Agda commands") { () =>
        extension
          .activate()
          .`then`(_ =>
            vscode.commands
              .getCommands()
              .`then`((commands: js.Array[String]) => {
                val expected = Set(
                  "agda-mode.connect",
                  "agda-mode.load",
                  "agda-mode.compile",
                  "agda-mode.quit",
                  "agda-mode.caseSplit",
                  "agda-mode.give",
                  "agda-mode.autoSimplified"
                )
                val missing = expected.diff(commands.toSeq.toSet)
                if (missing.nonEmpty) {
                  throw new Exception(s"Agda commands were not registered: ${missing.toSeq.sorted.mkString(", ")}")
                }
              })
          )
          .asInstanceOf[Thenable[Unit]]
      }

      test("Auto (Simplified) fills the selected hole and keeps the next hole") { () =>
        runAutoFillTest(extension).toJSPromise
      }

      test("hole normalization ignores Agda comments, strings, and existing holes") { () =>
        val source = "-- ? and {}\n{- ? {- {} -} ? -}\n\"? {}\" ? {} {! keep ? and {} !}"
        val expected = "-- ? and {}\n{- ? {- {} -} ? -}\n\"? {}\" {! !} {! !} {! keep ? and {} !}"
        val actual = agdaParsing.normalizeHoleSyntax(source)
        if (actual != expected) throw new Exception(s"Unexpected normalized holes: $actual")
        Future.successful(()).toJSPromise
      }
    }
  }

  private val agdaCode1 =
    """module AgdaAutoIntegration where
      |data Nat : Set where
      |  zero : Nat
      |  suc : Nat → Nat
      |_+_ : Nat → Nat → Nat
      |zero + n = ?
      |suc n + m = {}
      |""".stripMargin

  def getDocument(extension: vscode.Extension[_], str: String) = {
    val workspace = vscode.workspace.workspaceFolders.toOption
      .flatMap(_.headOption)
      .getOrElse(throw new Exception("The integration test requires an open workspace."))
    val uri = vscode.Uri.joinPath(workspace.uri, "AgdaAutoIntegration.agda")
    val config = vscode.workspace.getConfiguration("agdaMode")

    // write the Agda code to the test file
    nodeColonfsMod.writeFileSync(uri.fsPath, str)

    (
      config
        .update("connection.backend", "agda", vscode.ConfigurationTarget.Global)
        .toFuture
        .flatMap(_ => extension.activate())
        .flatMap(_ => vscode.workspace.openTextDocument(uri)),
      uri
    )
  }

  private def runAutoFillTest(
      extension: vscode.Extension[_]
  ): Future[Unit] = {

    val (documentF, uri) = getDocument(extension, agdaCode1)

    documentF
      .flatMap { document =>
        vscode.window.showTextDocument(document).toFuture.map(editor => (document, editor))
      }
      .flatMap { case (document, editor) =>
        eventually(
          Future.successful(document.getText().replace("\r\n", "\n")),
          text =>
            text.contains("zero + n = {! !}") &&
              text.contains("suc n + m = {! !}") &&
              !document.isDirty,
          "automatic load to normalize and save ? and {} holes"
        ).map { _ =>
          val firstHole = document.getText().indexOf("zero + n = {! !}")
          if (firstHole < 0) throw new Exception("Automatic load did not normalize the first goal.")
          val position = document.positionAt(firstHole + "zero + n = {! ".length)
          editor.selection = new vscode.Selection(position, position)
          (document, editor, position)
        }
      }
      .flatMap { case (document, _, position) =>
        eventually(
          codeActionsAt(document, position),
          actions => actions.exists(_.command.get.command == "agda-mode.autoSimplified"),
          "Auto (Simplified) code action after loading on open"
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
          val addedPosition = document.positionAt(addedHole + 2)
          eventually(
            codeActionsAt(document, addedPosition),
            actions => actions.exists(_.command.get.command == "agda-mode.autoSimplified"),
            "goal actions after automatic reload"
          )
        }.flatMap { _ =>
          vscode.commands.executeCommand[js.Any]("agda-mode.autoSimplified").toFuture
        }.flatMap(_ =>
          eventually(
            Future.successful(document.getText().replace("\r\n", "\n")),
            text =>
              text.contains("zero + n = n") &&
                text.contains("suc n + m = {! !}") &&
                text.contains("identity n = {! !}"),
            "Auto fill only the selected goal"
          )
        ).map(_ => ())
      }
      .andThen { case _ =>
        vscode.commands.executeCommand[js.Any]("agda-mode.quit")
        nodeColonfsMod.unlinkSync(uri.fsPath)
      }
  }

}
