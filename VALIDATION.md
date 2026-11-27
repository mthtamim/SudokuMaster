# Validation (2026-11-27, local static only)

## Checked and passed
- Every `R.id.*` used in MainActivity.kt (33 ids) exists in activity_main.xml; no missing ids, no unused layout ids.
- Every `@string/` and `@color/` reference resolves (app_name; bg/card/text/text_secondary/accent/on_accent).
- `bg_card.xml` fixed: `@color/sd_card` (SmartDialer colour) -> `@color/card`.
- Kotlin brace/paren balance: SudokuLogic.kt 0/0, SudokuBoardView.kt 0/0, MainActivity.kt 0/0.
- Solver algorithm sanity-checked in Python on a known hard puzzle (53..7.... etc.): backtracking with MRV returns the known solution 534678912...
- Manifest: no INTERNET permission, portrait only, MainActivity is launcher.
- Icon: custom 3x3 grid vector `ic_grid.xml`, launcher uses it; `ic_launcher_background` colour defined.

## NOT proven
- No Android SDK / Gradle in this VM, so no Gradle build, no APK, no CI run yet.
- No phone test yet. Real proof is CI green + Release APK verified + phone test.
- Generator uniqueness is enforced by `hasUniqueSolution` (countSolutions up to 2) in code, but not run on-device yet.
