package vscextension

import scala.scalajs.js
import scala.scalajs.js.Thenable

import typings.vscode.mod as vscode

object ExtensionHostIntegrationTest {
  private val extensionId = "test.vscode-scalajs-hello"

  def main(args: Array[String]): Unit = {
    val extension = vscode.extensions.getExtension[js.Any](extensionId)

    suite("Agda extension host") { () =>
      test("activates and registers Agda commands") { () =>
        if (!extension.isDefined) {
          throw new Exception(s"Extension '$extensionId' was not found in the extension host.")
        }

        extension.get
          .activate()
          .`then`((_: js.Any) =>
            vscode.commands.getCommands().`then`((commands: js.Array[String]) => {
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
    }
  }

  private def suite(name: String)(body: () => Unit): Unit =
    js.Dynamic.global.suite(name, body)

  private def test(name: String)(body: () => Thenable[Unit]): Unit =
    js.Dynamic.global.test(name, body)
}
