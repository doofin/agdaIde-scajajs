# improving and enhancing this template
To help developers write vscode extensions efficiently, we already provide some common utilities and facades:
- VSCode API facade by scalajs converter plugin
- Some examples of how to use the VSCode API facade
- language client facade


Examples inspired by the reference extensions:
- Agda: semantic highlighting, Unicode input, goal/type queries, and goal navigation.
- Metals: run/debug code lenses, test discovery, and executable worksheets.

Reusable utilities can cover command registration, configuration, file/network IO, status-bar items, and diagnostic decorations.

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

## Rounds of implementation
1st round is mainly about establishing a MVP(minimum viable product).

1st round instructions:
- able to connect to Agda/ALS servers
- implement syntax highlighting when opening Agda files
