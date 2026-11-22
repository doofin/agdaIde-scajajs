Here we analyze the original agda-mode-vscode extension


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
