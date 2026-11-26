# Implementation
Examples inspired by the reference extensions:
- Agda: semantic highlighting, Unicode input, goal/type queries, and goal navigation.
- Metals: run/debug code lenses, test discovery, and executable worksheets.

Reference vscode plugins:
- https://github.com/banacorn/agda-mode-vscode
- https://github.com/scalameta/metals-vscode

Principles of documentation editing
- don't delete the instructions
- only concisely describe the result
- be conservative when editing documentation

Principles of coding:
- write modular and reusable code,DRY
- use functional programming like immutability, pure functions, and higher-order functions
- use Option, Either, and other functional data types for error handling and optional values
- don't dig too much into pure FP, use while loops, etc rather than recursion everywhere

Tooling:
- use metals mcp if possible, rather than command line invocations

## Rounds of implementation
Overall plan is at [Impl-plan-agda.md](Impl-plan-agda.md)

1st round is mainly about establishing a MVP(minimum viable product).

1st round instructions:
- connect to Agda or ALS and notify if the connection succeeds
- provide syntax highlighting when opening Agda files
- ALS: follow the connection and download flow in the original extension under
  `src-ref/agda-mode-vscode`, including how it prompts, downloads, and starts a
  managed binary

1st round result:
- Implemented `.agda` language registration, a TextMate grammar, and basic
  language configuration.
- Opening an Agda file starts the configured backend (`agda` by default, or
  `als`) and reports success or failure. Direct Agda starts
  `agda --interaction` and waits for its prompt. ALS currently requires an
  installed executable configured via `agdaMode.connection.alsPath`; automatic
  download is not implemented yet.
- Added initial Load, Compile, and Quit Server commands. Load and Compile send
  Agda interaction requests to the direct process or ALS custom `agda` request;
  raw server output is written to the **Agda** Output channel.
- Direct Agda output now parses highlight-clear/add-annotations S-expressions
  and publishes VS Code semantic tokens after Load. ALS highlighting,
  diagnostics, literate Agda support, automatic server downloads, and the
  original extension's remaining features are still pending.


2nd round instructions:
- other necessary MVP features like basic commands.
- show a message for users for outcome of commands like Load and Compile

2nd round result:
- Added **Agda: Load**, **Agda: Compile**, and **Agda: Quit Server** commands
  to the Command Palette for Agda documents.
- Load and Compile now report completion through VS Code messages. Direct
  Agda uses its status/error/compilation responses; ALS uses its command
  response. Raw output remains available in the **Agda** Output channel.
- Direct Agda highlight annotations are parsed for editor semantic tokens;
  diagnostics and ALS highlighting are not implemented yet.


  3rd round instructions:
- implement contextual features like case split, give(fill goal) with code actions
- implement auto(simplified) for hole code actions
- implement contextual features like hover, go-to-definition, and code actions for Agda files

3rd round result:
- Added Agda code actions for case splitting and giving an expression in a
  loaded goal using the direct Agda backend. Give reuses a nonempty goal,
  applies Agda's Give response, and requests highlighting for the inserted
  expression without reloading the file. Hover and go-to-definition remain
  pending; ALS goal actions are not supported yet.
- Added **Agda: Auto (Simplified)** to hole code actions using Agda's
  `Cmd_autoOne Simplified` request. This request form requires Agda 2.7 or later.


4th round instructions:
- improve and enhance this project with src-ref/vscode-scalajs-hello
- check previous implementation of agda auto(which fills holes), and add integration test for it

5th round instructions:
- when doing agda load, support using ? and {} for holes in the agda file, and replace them with appropriate Agda hole syntax and save the file before sending the load request. underline the holes in the editor.
- Add shortcut support for agda commands like Load(C-c l), Compile(C-c c), and Auto (Simplified)(C-c a), and allow user to configure keybindings for them.
- split parsing(Scala code that doen't depend on vscode) code into agdaParsing and add munit unit tests for it under src/test
- check this proj to see if we can add more pure unit tests
- auto load Agda files when they are opened. also auto load when holes are changed.

5th round result:
- On Load, `?` and `{}` are converted to `{! !}` outside comments, strings,
  and existing holes; the document is saved before the Agda load request.
- Agda holes are underlined in visible editors and the decoration updates as
  the document changes. The underline color is configurable with
  `agdaMode.holes.decorationColor` (default: `yellow`).
- Added default `Ctrl+C` chord shortcuts for Load (`Ctrl+L`), Compile
  (`Ctrl+C`), and Auto (Simplified) (`Ctrl+A`). Users can customize these
  contributed keybindings in VS Code Keyboard Shortcuts.
- Extracted VS Code-independent S-expression and hole parsing into
  `agdaParsing`, with MUnit coverage under `src/test`. Root `sbt test` runs
  these tests under Node.js in an isolated Scala.js test project, so they do
  not load VS Code's host-provided module.
- Agda documents are loaded automatically when opened. Edits are debounced
  per document and trigger a reload when a hole existed before or after the
  edit; load normalization and code-action edits are excluded to avoid
  redundant loads. The scheduling and document tracking live in
  `AgdaAutoLoad`; direct-backend output is associated with the document whose
  command is running.
- `agdaAutoLoadSuite` separately verifies normalization and loading on open,
  then inserts another hole and checks that automatic reload restores goal
  actions.


  6th round instructions:
  - go to definition(and show references): think how to implement this feature, don't code it yet
  - ok impl it(go to definition) only for agda backend, no als yet
  - show type information for the symbol under the cursor.
  - "go to symbol in editor": list the functions and data types in the current file.  think how to implement this feature, don't code it yet
  - Show or Focus Hover (editor.action.showHover). Displays the code documentation or type tooltip under your cursor.