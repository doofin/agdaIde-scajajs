# Agda Mode for VS Code in Scala.js

This repository is a work-in-progress migration of
[agda-mode-vscode](https://github.com/banacorn/agda-mode-vscode) to Scala.js.
The goal is to bring the extension's Agda editing features to a Scala.js
implementation; this project is not yet a feature-complete replacement.

## Status

The first MVP slice now provides:

- Agda syntax coloring and basic editor bracket/comment behavior for `.agda`
  files
- A configurable connection attempt on opening an Agda file: ALS via its
  `agda` LSP request handshake, or Agda via `agda --interaction` and its prompt

This is only a connection and syntax-highlighting MVP, not feature parity. The
Scala.js extension currently supports basic Load and Compile commands, with raw
server output in the **Agda** Output channel and VS Code messages reporting
command outcomes. For direct Agda connections, Load also converts Agda's
highlight annotations into VS Code semantic tokens. ALS semantic-token
decoding and highlight decorations are not implemented yet.
Literate Agda files, automatic server downloads, Unicode input, and goal
operations are also still pending.

## Get started

Requirements: [Node.js/npm](https://nodejs.org/), [sbt](https://www.scala-sbt.org/),
and VS Code with the `code` command available on your `PATH`. The
[Metals](https://scalameta.org/metals/) extension is recommended for Scala
development.

1. Clone the repository and open it in VS Code. Let Metals import the sbt build.
2. Run `sbt open` from the project root. This compiles the extension, prepares
   its output, and opens a VS Code Extension Development Host. The first run may
   take a while.
3. Open an `.agda` file. Agda syntax coloring is contributed by the extension.
4. By default, the extension starts `agda --interaction` and waits for Agda's
   prompt. To use ALS instead, set `agdaMode.connection.backend` to `als`.
   Executable names can be overridden with `agdaMode.connection.agdaPath` and
   `agdaMode.connection.alsPath`. If VS Code cannot find an executable that is
   available in your terminal, set its path to the executable's absolute path.
5. Run **Agda: Load** or **Agda: Compile** from the Command Palette. Use
   **Agda: Connect to Server** to retry a failed connection and **Agda: Quit
   Server** to stop it.

## Build and package

Compile the Scala.js extension:

```bash
sbt fastOptJS prepareExtension
```

To package the extension as a `.vsix` (with `vsce` available through `npx`):

```bash
cd out && npx vsce package
```

## Testing

Run the MUnit tests with `sbt test`. They compile to JavaScript and run under
Node.js. The VS Code runtime module `vscode` is provided by the extension host,
not by the `@types/vscode` typings in `build.sbt`; tests that load extension
code using VS Code APIs therefore need an extension-host test runner.

For examples, the [Agda extension](https://github.com/banacorn/agda-mode-vscode)
uses `@vscode/test-electron`, while
[Metals](https://github.com/scalameta/metals-vscode) separates Node unit tests
from extension-host tests. Keep pure logic in unit tests and test VS Code
integration in the host.

## Project layout

- `src/main/scala/vscextension/` — Scala.js extension entry point and VS Code
  feature examples
- `src/main/scala/vscextension/facade/` — VS Code and language-client facades
- `src/main/scala/vscextension/io/` — configuration, file, and network helpers
- `server/` — Node.js language-server example
- `build.sbt` — Scala.js, ScalablyTyped, and npm dependency configuration

The original extension is available at
[banacorn/agda-mode-vscode](https://github.com/banacorn/agda-mode-vscode).
`src-ref/` is a local reference checkout and is excluded from version control.

## Scala.js and VS Code APIs

VS Code APIs are available through the generated ScalablyTyped facade:

```scala
import typings.vscode.mod as vscode

vscode.window.showInformationMessage("Hello from Scala.js!")
```

Scala.js JavaScript types are available from `scala.scalajs.js`. Prefer the
generated API types; use `.asInstanceOf[Type]` only where a generated facade
does not express the VS Code API accurately.

## Contributing

Contributions toward migrating Agda-mode features are welcome. Keep pure logic
separate from VS Code API integration so it can be tested under Node.js, and add
extension-host tests for behavior that requires VS Code.

Useful references:

- [Agda mode for VS Code](https://github.com/banacorn/agda-mode-vscode)
- [Agda Language Server](https://github.com/agda/agda-language-server)
- [VS Code Extension API](https://code.visualstudio.com/api)
- [Scala.js](https://www.scala-js.org/)
- [ScalablyTyped](https://scalablytyped.org/)
