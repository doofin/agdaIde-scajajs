package vscextension

import scala.language.implicitConversions

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
import typings.std.PromiseLike

object testUtils {
  object implicitConv {
    implicit def promiseLikeToFuture[A](p: PromiseLike[A]): Future[A] =
      p.toFuture
  }

  def codeActionsAt(
      document: vscode.TextDocument,
      position: vscode.Position
  ): Future[Vector[vscode.CodeAction]] =
    vscode.commands
      .executeCommand[js.Array[vscode.CodeAction]](
        "vscode.executeCodeActionProvider",
        document.uri,
        new vscode.Range(position, position)
      )
      .toFuture
      .map(_.toVector)

  /** This utility repeatedly evaluates a Future until a condition is met or a timeout occurs.
    *
    * @param read
    *   The Future to be evaluated repeatedly.
    * @param isReady
    *   A function that checks if the result of the Future satisfies the desired condition.
    * @param description
    *   A description of what is being waited for, used in the timeout error message.
    * @param attemptsRemaining
    *   The number of remaining attempts before timing out.
    * @return
    *   A Future that completes successfully when the condition is met or fails if the timeout occurs.
    */
  def eventually[A](
      read: => Future[A],
      isReady: A => Boolean,
      description: String,
      attemptsRemaining: Int = 120
  ): Future[A] =
    read.flatMap { value =>
      if (isReady(value)) Future.successful(value)
      else if (attemptsRemaining == 0)
        Future.failed(new AssertionError(s"Timed out waiting for $description"))
      else {
        val delay = Promise[Unit]()
        setTimeout(250)(delay.success(()))
        delay.future.flatMap(_ => eventually(read, isReady, description, attemptsRemaining - 1))
      }
    }

  // def suite(name: String)(body: () => Unit): Unit =
  //   js.Dynamic.global.suite(name, body)

  // def test(name: String)(body: () => Thenable[Unit]): Unit =
  //   js.Dynamic.global.test(name, body)
}
