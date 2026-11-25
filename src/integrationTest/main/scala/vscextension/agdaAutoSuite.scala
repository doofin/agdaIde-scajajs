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
    }
  }

  private val agdaCode1 =
    """module AgdaAutoIntegration where
      |data Nat : Set where
      |  zero : Nat
      |  suc : Nat → Nat
      |_+_ : Nat → Nat → Nat
      |zero + n = {!   !}
      |suc n + m = {!   !}
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
        vscode.window.showTextDocument(document).toFuture.map { editor =>
          val holeOffset = document.getText().indexOf("{!")
          if (holeOffset < 0) throw new Exception("Auto integration fixture has no goal.")
          val position = document.positionAt(holeOffset + 2)
          editor.selection = new vscode.Selection(position, position)
          (document, position)
        }
      }
      .flatMap { case (document, position) =>
        vscode.commands
          .executeCommand[js.Any]("agda-mode.load")
          .flatMap(_ =>
            eventually(
              codeActionsAt(document, position),
              { (actions) =>
                actions.exists(action =>
                  action.command.get.command
                    == "agda-mode.autoSimplified"
                )
              },
              "Auto (Simplified) code action"
            )
          )
          .flatMap(_ => vscode.commands.executeCommand[js.Any]("agda-mode.autoSimplified").toFuture)
          .flatMap(_ =>
            eventually(
              Future.successful(document.getText().replace("\r\n", "\n")),
              text =>
                text.contains("zero + n = n") &&
                  text.contains("suc n + m = {!   !}"),
              "Auto fill only the selected goal"
            )
          )
          .map(_ => ())
      }
      .andThen { case _ =>
        vscode.commands.executeCommand[js.Any]("agda-mode.quit")
        nodeColonfsMod.unlinkSync(uri.fsPath)
      }
  }

}
