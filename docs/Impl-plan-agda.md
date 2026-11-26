# Migrating agda-mode-vscode to Scala.js

This is a migration guide for the original
[agda-mode-vscode](https://github.com/banacorn/agda-mode-vscode),The original behavior is implemented in ReScript

The local reference is `src-ref/agda-mode-vscode/`. It is ignored by Git.

## What the original extension does

The extension connects Agda documents to either:

1. **Agda directly**, by launching `agda --interaction`, parsing its
   incremental S-expression output, and sending interaction commands; or
2. **Agda Language Server (ALS)**, by using LSP transport and ALS-specific
   request/response payloads. ALS can run as a native process, over TCP, or
   through the `qbane.als-wasm-loader` extension.

Its main responsibilities are broader than command registration:

- Connect, probe, select, download, switch, and shut down Agda/ALS candidates.
- Keep one shared connection per VS Code window while maintaining state for
  each loaded document.
- Encode Agda commands and parse both direct Agda output and ALS responses.
- Track interaction points (goals), edit goal contents, and move between goals.
- Parse and update Agda semantic highlighting, including edits and cross-file
  source locations.
- Provide Unicode input with candidate browsing, editor rewrites, and a
  webview-based keyboard/picker.
- Support Agda and literate Agda language IDs, including desktop and web
  extension entry points.

## Architecture

| Original area                                                                                                                 | Responsibility                                                                          | Scala.js destination                                                                          |
| ----------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `src/Main/Main.res`                                                                                                           | Activation, VS Code events, per-document initialization, command routing, providers     | `src/main/scala/vscextension/extensionMain.scala` plus focused feature modules                |
| `src/Main/Desktop.res`, `src/Main/Web.res`, `src/Platform/Platform.res`                                                       | Inject host-specific command lookup, download policy, and WASM support                  | A typed platform interface; keep Node-only APIs out of common feature logic                   |
| `src/Connection/Connection__Core.res`, `src/Connection/Endpoint/*`, `src/Connection/Transport/*`, `src/Connection/Protocol/*` | Candidate probing, process/TCP/WASM transports, Agda/ALS lifecycles, protocol messaging | New connection and protocol modules; reuse the existing language-client facade where suitable |
| `src/Registry.res`, `src/State/State.res`, `src/Registry__Connection.res`                                                     | Per-document state and shared connection lifecycle/serialization                        | Separate document-state registry from a per-window connection manager                         |
| `src/Command.res`, `src/Request.res`, `src/Response.res`, `src/State/State__Command.res`, `src/State/State__Response.res`     | Command model, wire encoding, response decoding, UI/state effects                       | Typed Scala command/request/response models and explicit protocol codecs                      |
| `src/Goals.res`, `src/Goal.res`, `src/Tokens.res`, `src/Highlighting/*`                                                       | Goal ranges, incremental edits, tokens, decorations, definitions                        | Pure state/coordinate logic separated from VS Code API adapters                               |
| `src/InputMethod/*`, `src/View/*`, `src/View/WebviewPanel.res`                                                                | Unicode translation, candidate state, prompt/panel UI                                   | Port translation and buffer logic as pure Scala first; then add editor and webview adapters   |
| `src/Config.res`, `src/Connection/Download/*`                                                                                 | Settings, candidate ordering, release selection, download/storage                       | Typed config and download services, independent from editor state                             |

Two lifetimes are especially important: a document's state is created and
destroyed as the document is loaded/closed, while the connection can remain
alive for other documents. Do not make each editor state own and terminate its
own server. The original connection design is also described in
`src-ref/agda-mode-vscode/docs/specifications/window-scoped-connection-sharing.md`;
its lifecycle,
serialization, and teardown behavior should be treated as requirements, not
incidental implementation details.

## steps and features

### Introduce typed document state and commands

Once load works, add a sealed command model and a state object per loaded
document. Keep VS Code event registration at the extension boundary; route
events to state by document URI, and reject commands for non-Agda documents.
The original also refuses Agda commands for Live Share guest documents.

Port commands incrementally, grouped by response behavior:

- Load, compile, quit, restart, and display toggles.
- Goal navigation and goal queries: goal type, context, constraints, and
  module contents.
- Goal edits: give, refine, case split, auto, and elaborate-and-give.
- Global queries: infer type, search, normal form, and why-in-scope.

Use one codec for each protocol boundary. Keep command intent separate from
version-specific Agda wire syntax; the original `Request.res` contains
version-dependent encodings and normalization modes that should not be
flattened into string concatenation at call sites.

### offsets-sensitive logic

Goals and highlighting depend on exact offsets, so isolate them as pure logic
and test them before wiring in decorations. VS Code uses UTF-16 offsets;
Agda's output can use code-point offsets and LF-normalized line endings. The
original explicitly converts astral Unicode characters and CRLF offsets in
`src/Agda.res`. Also preserve goal positions across multiple edits, refine/give
operations, and asynchronous server responses.

For highlighting, first decode Agda/ALS tokens into a typed model, then map
token types and modifiers to the VS Code semantic-token legend. The current
`src/main/scala/vscextension/SyntaxHighlighting.scala` only demonstrates
highlighting `TODO` in Scala; it is not Agda highlighting.

The VS Code-facing Scala.js code follows this shape:

```scala
val builder = new vscode.SemanticTokensBuilder(legend)
builder.push(line, start, length, tokenTypeIndex)
builder.build()
```

### Unicode input and the panel

Port the translator and input buffer independently of VS Code. Then implement
editor selection tracking, decorations, candidate navigation, and batched
text edits. Finally port the webview message protocol and prompt UI.

The translation table (`asset/query.js`) and the ReScript `View` components are
part of the behavior. Preserve input in both the editor and prompts, keybinding
contexts, cleanup when switching documents, and the behavior when edits or
cursor movement happen while a rewrite is pending.

## Testing and completion criteria

Use the original tests as a behavior inventory, not as a requirement to
translate every test file literally. Port pure logic into MUnit tests and add
VS Code extension-host tests for editor, command, webview, and lifecycle
integration. Keep small `.agda` fixtures for realistic protocol behavior.

Prioritize cases that are easy to regress:

- Request/response codecs across supported Agda and ALS versions.
- Fragmented direct-Agda output, prompt boundaries, malformed responses, and
  server errors.
- Connection sharing, serialized requests, closing a document during connect,
  and teardown after failure.
- Unicode astral characters, CRLF, multi-edit updates, and goal movement.
- Token range rebasing and refresh after load, edits, and refine/give.
- Unicode input candidate selection and editor rewrite behavior.
- Desktop and browser-specific executable, WASM, and URI behavior.

Treat a feature as migrated only when its behavior is implemented, its
contributed VS Code metadata is present, and its relevant pure or
extension-host tests pass. Until then, document it as planned or in progress;
the existing sample code is not evidence of Agda feature parity.


## go to definition
The original extension gets definition locations from Agda’s highlighting annotations, rather than searching names in source. Its `token parser` preserves an optional source filepath and offset. `Tokens` looks up the annotation at the cursor and resolves the source location; `Main` registers the provider. The current `agdaParsing.scala` parses highlight ranges and token types, but discards that source metadata.

Suggested design:

1. Extend the parsed highlight data with an optional definition source, extracting it from the detailed Agda annotation form. Leave it absent when the annotation has no source.
2. Store source locations alongside highlight ranges in a dedicated definition index, keyed by document URI. Clear the index when the document changes or a new load starts; only use results from a load that still matches the document version.
3. Add a VS Code definition provider, preferably in a separate `AgdaDefinitionProvider` component. It should find the source annotation under the cursor, open its target document, convert the target offset to a VS Code position, and return no result when no valid source is available. Registering a provider enables F12/Peek Definition; no new command or keybinding is needed.
4. For the direct backend, preserve load-time document text and offset conversion. Agda uses one-based code-point offsets, while VS Code uses UTF-16 offsets; CRLF and edits during a load can otherwise send navigation to the wrong location. The original implementation treats same-file and cross-file offsets differently, so preserve that distinction.
5. For ALS, first check whether its language server already advertises the standard LSP definition capability. The LanguageClient should register that provider automatically; avoid adding a competing provider unless ALS lacks support.

I’d test source-metadata parsing and Unicode/CRLF conversion separately, then add an extension-host test that navigates from a reference to a symbol in another module. The main risk is stale locations after edits or while a load is in flight; dropping the index until a fresh load completes is the simpler safe behavior.
