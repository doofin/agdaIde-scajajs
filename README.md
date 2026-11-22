# Agda Mode for VS Code in Scala.js

This repository is a work-in-progress migration of
[agda-mode-vscode](https://github.com/banacorn/agda-mode-vscode) to Scala.js.
The goal is to bring the extension's Agda editing features to a Scala.js
implementation; this project is not yet a feature-complete replacement.

## Status

The Scala.js extension currently provides examples and infrastructure for:

- VS Code commands, code actions, inline completions, and status-bar items
- A sample semantic token provider that highlights `TODO` in Scala files
- A sample language client for Plain Text documents, with completions and
  uppercase-word diagnostics
- Configuration, file and network I/O, and VS Code API facades

Agda-specific functionality from the original extension, such as Agda language
server integration and Unicode input, has not yet been migrated.

## Get started

Requirements: [Node.js/npm](https://nodejs.org/), [sbt](https://www.scala-sbt.org/),
and VS Code with the `code` command available on your `PATH`. The
[Metals](https://scalameta.org/metals/) extension is recommended for Scala
development.

1. Clone the repository and open it in VS Code. Let Metals import the sbt build.
2. Run `sbt open` from the project root. This compiles the extension, prepares
   its output, and opens a VS Code Extension Development Host. The first run may
   take a while.
3. In the new window, open the Command Palette and run **Hello World**.
4. To try the language-server example, open a Plain Text document, type `AAA`,
   and confirm the warning appears. Request completion to see TypeScript and
   JavaScript suggestions.

## Build and package

Compile the Scala.js extension and prepare its language-server files:

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
