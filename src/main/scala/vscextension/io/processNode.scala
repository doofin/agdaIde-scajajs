package vscextension.io

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import typings.node.childProcessMod._
import typings.node.nodeColonstreamMod

// TODO: WIP. Don't use yet.
object processNode {
  def spawnProcess(command: String, args: js.Array[String], cwd: String): typings.node.childProcessMod.ChildProcess = {
    val ps = spawn(command, args, SpawnOptions().setCwd(cwd))
    val rs: nodeColonstreamMod.Readable = Option(ps.stdout).get
    ps
  }
}
