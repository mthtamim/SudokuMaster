# Sudoku Master

Native Android Sudoku app (Kotlin + XML Views, offline, no ads, no internet).

Package: `com.mthtamim.sudoku` — v1.0.0 (versionCode 1)

## Modes
- **Play** — Generate puzzles with a unique solution: Easy (40 givens), Medium (34), Hard (30), Expert (26). Timer, 3-mistake game over, Notes/Pencil mode with auto cleanup, Undo, Erase, Hint (with technique name), Check, same-number + row/col/box highlight, remaining counts, save/resume, Daily Puzzle via New Game, Stats (played/won/best time per difficulty).
- **Solver** — Solves ANY Sudoku, not only generated ones. Enter by tapping cells or paste 81 chars (digits, `0` or `.` for empty). Validates input, then solves with logical techniques (Naked Single, Hidden Single) + backtracking, so Hard/Expert puzzles that need guessing are solved too. Shows step-by-step log (first 30 steps), reports No Solution / Multiple Solutions (counts up to 2), copies the solution to clipboard.
- **Creator** — Enter givens, check validity + unique solution, estimate difficulty, Generate a new puzzle, Save to local library, Share as 81-char text.

## Build
CI builds the debug APK (see workflow note below). Local: Android Studio / Gradle 8.7, JDK 21, AGP 8.6.1, compileSdk 34, minSdk 24.

## Workflow note (browser step, NOT in the push set)
The GitHub App has no Workflows permission, so `.github/workflows/android.yml` must be added via the browser BEFORE the code push. The code push excludes it. See `sudoku-app-workflow-android.yml` in the workspace (outside this folder).

## v1.0.1 (Phase 1, Ultimate plan)
UI fix only, no logic change: number pad is now a 3x3 grid with 56dp dark buttons (digits 1-9 visible at 22sp); every button sets minWidth/minHeight 0, inset 0, maxLines 1; action buttons split into rows of 3 (Notes/Erase/Undo, Hint/Check/New Game, Solve/Clear/Stats); Notes label shortened to "Notes" / "Notes ✓"; Save Puzzle shortened to "Save".
