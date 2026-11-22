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
- This milestone establishes connectivity only. Sending Agda commands,
  processing server highlighting, literate Agda support, and the original
  extension's remaining features are still pending.

### Original ALS connection and download flow

The original extension uses a broader fallback flow than the current MVP:

1. `State__Connection.sendRequest` calls `Connection.makeWithFallback` when a
   document first needs a server connection.
2. The default `agdaMode.connection.paths` is `["agda", "als"]`. Candidates are
   tried from last to first, so `als` is tried before `agda`. A candidate may be
   a command found on `PATH` or a configured resource path. The extension probes
   it to distinguish Agda from ALS before starting a connection.
3. If all configured candidates fail, the extension checks persisted
   `connection.downloadPolicy`. The default is `Undecided`, which prompts the
   user; `Yes` permits download and `No` suppresses it. Thus first-run download
   is automatic only after user consent, not an unconditional silent download.
4. It first reuses a compatible managed download, otherwise resolves the
   selected release channel (`dev` by default; `latest` is also supported) from
   the `agda/agda-language-server` GitHub releases. The selected asset must
   match the host platform. Native artifacts are supported on Windows, macOS
   x64/arm64, and Ubuntu 24.04+ x86_64. Other hosts use a WASM artifact and
   require `qbane.als-wasm-loader`.
5. Downloads are stored under VS Code global storage in a release/artifact
   directory. Native zip files are extracted and made executable. The resolved
   binary is then passed to the language client as a process command; ALS is
   initialized with its custom `agda` LSP request and `CmdReqSYN` payload.
   Failed native downloads or startup can fall back to a matching WASM build
   when its loader is available.
6. On successful automatic fallback, the managed binary path is added to
   `connection.paths` at lowest priority, so explicit user candidates remain
   preferred on subsequent connections.

The main reference points are `src/State/State__Connection.res`,
`src/Connection/Connection__Core.res`,
`src/Connection/Endpoint/Connection__Endpoint__ALS.res`,
`src/Main/Desktop.res`, and `src/Connection/Download/`. These are in the local
reference checkout; use the upstream repository if `src-ref/` is absent.

2nd round instructions:
- other necessary MVP features