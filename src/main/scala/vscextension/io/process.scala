package vscextension.io

import scala.scalajs.js

import typings.node.childProcessMod.{
  ChildProcess as NodeChildProcess,
  ChildProcessWithoutNullStreams,
  SpawnOptionsWithoutStdio,
  spawn as spawnNode
}
import typings.node.bufferMod.global.BufferEncoding
import typings.node.processMod.global.NodeJS.Signals
import typings.node.streamMod.{Readable, Writable}

object process {
  type ChildProcess = ChildProcessWithoutNullStreams & NodeChildProcess & js.Object {
    val stdin: Writable
    val stdout: Readable
    val stderr: Readable
  }

  def spawn(command: String, args: js.Array[String], cwd: String): ChildProcess = {
    val options: SpawnOptionsWithoutStdio = SpawnOptionsWithoutStdio().setCwd(cwd)
    spawnNode(command, args, options).asInstanceOf[ChildProcess]
  }

  def writeText(stream: Writable, value: String): Boolean =
    stream.write(value.asInstanceOf[js.Any], BufferEncoding.utf8)

  def onExit(
      child: ChildProcess
  )(listener: js.Function2[Double | Null, Signals | Null, Unit]): Unit =
    child.on_exit(
      typings.node.nodeStrings.exit,
      listener
    )
}
