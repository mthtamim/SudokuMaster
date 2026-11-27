package com.mthtamim.sudoku

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    enum class Mode { PLAY, SOLVER, CREATOR }

    private lateinit var boardView: SudokuBoardView
    private lateinit var tvStatus: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvStats: TextView
    private lateinit var etImport: EditText
    private lateinit var btnNotes: Button

    private var mode = Mode.PLAY
    private var difficulty = SudokuLogic.Difficulty.EASY
    private var puzzle = IntArray(81)
    private var solution = IntArray(81)
    private var board = IntArray(81)
    private var givens = BooleanArray(81)
    private var notesMode = false
    private var mistakes = 0
    private var seconds = 0
    private var timerRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val undoStack = ArrayDeque<Triple<Int, Int, Set<Int>>>()
    private val prefs by lazy { getSharedPreferences("sudoku", Context.MODE_PRIVATE) }

    private val timerTick = object : Runnable {
        override fun run() {
            if (timerRunning) {
                seconds++
                updateStatus()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        boardView = findViewById(R.id.boardView)
        tvStatus = findViewById(R.id.tvStatus)
        tvSteps = findViewById(R.id.tvSteps)
        tvStats = findViewById(R.id.tvStats)
        etImport = findViewById(R.id.etImport)
        btnNotes = findViewById(R.id.btnNotes)

        boardView.onCellSelected = { boardView.invalidate() }
        findViewById<Button>(R.id.btnPlay).setOnClickListener { setMode(Mode.PLAY) }
        findViewById<Button>(R.id.btnSolver).setOnClickListener { setMode(Mode.SOLVER) }
        findViewById<Button>(R.id.btnCreator).setOnClickListener { setMode(Mode.CREATOR) }
        findViewById<Button>(R.id.btnEasy).setOnClickListener { difficulty = SudokuLogic.Difficulty.EASY; newGame() }
        findViewById<Button>(R.id.btnMedium).setOnClickListener { difficulty = SudokuLogic.Difficulty.MEDIUM; newGame() }
        findViewById<Button>(R.id.btnHard).setOnClickListener { difficulty = SudokuLogic.Difficulty.HARD; newGame() }
        findViewById<Button>(R.id.btnExpert).setOnClickListener { difficulty = SudokuLogic.Difficulty.EXPERT; newGame() }
        findViewById<Button>(R.id.btnNewGame).setOnClickListener { newGame() }
        btnNotes.setOnClickListener { notesMode = !notesMode; btnNotes.text = if (notesMode) "Notes ✓" else "Notes" }
        findViewById<Button>(R.id.btnErase).setOnClickListener { eraseSelected() }
        findViewById<Button>(R.id.btnUndo).setOnClickListener { undo() }
        findViewById<Button>(R.id.btnHint).setOnClickListener { hint() }
        findViewById<Button>(R.id.btnCheck).setOnClickListener { checkBoard() }
        findViewById<Button>(R.id.btnSolveBoard).setOnClickListener { solveCurrent() }
        findViewById<Button>(R.id.btnClearBoard).setOnClickListener { clearBoard() }
        findViewById<Button>(R.id.btnStats).setOnClickListener { showStats() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { importText() }
        findViewById<Button>(R.id.btnGenerate).setOnClickListener { newGame() }
        findViewById<Button>(R.id.btnSavePuzzle).setOnClickListener { savePuzzle() }
        val numIds = listOf(R.id.btnNum1, R.id.btnNum2, R.id.btnNum3, R.id.btnNum4, R.id.btnNum5,
            R.id.btnNum6, R.id.btnNum7, R.id.btnNum8, R.id.btnNum9)
        numIds.forEachIndexed { i, id -> findViewById<Button>(id).setOnClickListener { enterNumber(i + 1) } }

        // Resume saved Play game if present, else start Easy.
        val saved = prefs.getString("saved_board", null)
        if (saved != null && SudokuLogic.fromText(saved) != null) {
            val b = SudokuLogic.fromText(saved)!!
            val p = SudokuLogic.fromText(prefs.getString("saved_puzzle", saved)!!) ?: b
            val s = SudokuLogic.fromText(prefs.getString("saved_solution", saved)!!) ?: b
            puzzle = p; solution = s; board = b
            givens = BooleanArray(81) { puzzle[it] != 0 }
            seconds = prefs.getInt("saved_seconds", 0)
            syncView()
            startTimer()
        } else {
            newGame()
        }
        updateStatus()
    }

    private fun setMode(m: Mode) {
        mode = m
        timerRunning = (m == Mode.PLAY)
        if (m == Mode.PLAY) startTimer() else handler.removeCallbacks(timerTick)
        when (m) {
            Mode.PLAY -> { tvSteps.text = "Play: fill the grid. Same numbers are highlighted. 3 mistakes = game over."; newGameIfEmpty() }
            Mode.SOLVER -> { tvSteps.text = "Solver: enter ANY Sudoku by tapping cells, or paste 81 chars below, then press Solve. It handles Easy to Expert, including puzzles that need guessing."; clearBoardForInput() }
            Mode.CREATOR -> { tvSteps.text = "Creator: enter givens, then Save Puzzle. It checks validity and unique solution, and estimates difficulty."; clearBoardForInput() }
        }
        updateStatus()
    }

    private fun newGameIfEmpty() { if (board.all { it == 0 }) newGame() }

    private fun newGame() {
        val (p, s) = SudokuLogic.generate(difficulty, Random(System.currentTimeMillis()))
        puzzle = p; solution = s; board = p.copyOf()
        givens = BooleanArray(81) { puzzle[it] != 0 }
        boardView.notes = Array(81) { mutableSetOf() }
        boardView.wrong = BooleanArray(81)
        undoStack.clear(); mistakes = 0; seconds = 0
        computeAllNotes()
        syncView()
        startTimer()
        tvSteps.text = "New ${difficulty.label} game started. ${puzzle.count { it != 0 }} givens."
        updateStatus()
        saveGame()
    }

    private fun clearBoardForInput() {
        board = IntArray(81); givens = BooleanArray(81)
        boardView.notes = Array(81) { mutableSetOf() }
        boardView.wrong = BooleanArray(81)
        undoStack.clear()
        syncView()
    }

    private fun clearBoard() {
        if (mode == Mode.PLAY) { newGame(); return }
        clearBoardForInput()
        tvSteps.text = "Board cleared."
    }

    private fun syncView() {
        boardView.board = board
        boardView.givens = givens
        boardView.selected = boardView.selected.coerceIn(0, 80)
        boardView.invalidate()
    }

    private fun startTimer() {
        timerRunning = true
        handler.removeCallbacks(timerTick)
        handler.postDelayed(timerTick, 1000)
    }

    private fun updateStatus() {
        val mm = seconds / 60; val ss = seconds % 60
        val modeName = when (mode) { Mode.PLAY -> difficulty.label; Mode.SOLVER -> "Solver"; Mode.CREATOR -> "Creator" }
        tvStatus.text = "$modeName  •  %02d:%02d  •  Mistakes %d/3".format(mm, ss, mistakes)
    }

    private fun computeAllNotes() {
        for (i in 0 until 81) {
            if (board[i] == 0) boardView.notes[i] = SudokuLogic.candidates(board, i / 9, i % 9).toMutableSet()
            else boardView.notes[i] = mutableSetOf()
        }
    }

    private fun enterNumber(v: Int) {
        val sel = boardView.selected
        if (sel !in 0..80) { toast("Tap a cell first"); return }
        if (mode == Mode.PLAY && givens[sel]) { toast("That cell is a given"); return }
        if (notesMode) {
            if (board[sel] != 0) return
            val set = boardView.notes[sel]
            if (set.contains(v)) set.remove(v) else set.add(v)
            boardView.invalidate(); return
        }
        undoStack.addLast(Triple(sel, board[sel], boardView.notes[sel].toSet()))
        board[sel] = v
        boardView.notes[sel] = mutableSetOf()
        boardView.wrong[sel] = false
        if (mode == Mode.PLAY) {
            if (solution[sel] != v) {
                boardView.wrong[sel] = true
                mistakes++
                updateStatus()
                if (mistakes >= 3) {
                    timerRunning = false
                    AlertDialog.Builder(this).setTitle("Game Over")
                        .setMessage("3 mistakes. Try a new game?")
                        .setPositiveButton("New Game") { _, _ -> newGame() }
                        .setNegativeButton("Keep Playing") { _, _ -> mistakes = 0; updateStatus() }
                        .show()
                    boardView.invalidate(); return
                }
            } else {
                // Remove this value from notes of peers.
                for (i in 0 until 81) boardView.notes[i].remove(v)
                if (board.all { it != 0 } && board.contentEquals(solution)) {
                    timerRunning = false
                    recordWin()
                    AlertDialog.Builder(this).setTitle("You solved it!")
                        .setMessage("Time: %02d:%02d".format(seconds / 60, seconds % 60))
                        .setPositiveButton("New Game") { _, _ -> newGame() }
                        .setNegativeButton("OK", null).show()
                }
            }
        }
        boardView.invalidate()
        saveGame()
    }

    private fun eraseSelected() {
        val sel = boardView.selected
        if (sel !in 0..80 || givens[sel]) return
        undoStack.addLast(Triple(sel, board[sel], boardView.notes[sel].toSet()))
        board[sel] = 0; boardView.wrong[sel] = false
        boardView.notes[sel] = SudokuLogic.candidates(board, sel / 9, sel % 9).toMutableSet()
        boardView.invalidate(); saveGame()
    }

    private fun undo() {
        val last = undoStack.removeLastOrNull() ?: return
        board[last.first] = last.second
        boardView.notes[last.first] = last.second.let { last.third.toMutableSet() }
        boardView.wrong[last.first] = false
        boardView.invalidate(); saveGame()
    }

    private fun hint() {
        val empty = (0 until 81).filter { board[it] == 0 && !givens[it] }
        if (empty.isEmpty()) { toast("Board is full"); return }
        // Prefer a cell solvable by logic, else use the known solution in Play.
        val (solved, steps) = SudokuLogic.solve(board)
        val step = steps.firstOrNull { board[it.row * 9 + it.col] == 0 }
        val target = step ?: run {
            val i = empty.first()
            val v = if (mode == Mode.PLAY && solution[i] != 0) solution[i] else solved?.get(i) ?: return
            SudokuLogic.Step(i / 9, i % 9, v, "Solution")
        }
        val i = target.row * 9 + target.col
        undoStack.addLast(Triple(i, board[i], boardView.notes[i].toSet()))
        board[i] = target.value
        givens[i] = false
        boardView.notes[i] = mutableSetOf()
        boardView.selected = i
        tvSteps.text = "Hint: row ${target.row + 1}, col ${target.col + 1} = ${target.value} (${target.technique})"
        boardView.invalidate(); saveGame()
    }

    private fun checkBoard() {
        var wrongCount = 0
        for (i in 0 until 81) {
            if (board[i] != 0 && !givens[i]) {
                val correct = when (mode) {
                    Mode.PLAY -> solution[i] == board[i]
                    else -> {
                        val v = board[i]
                        board[i] = 0
                        val ok = SudokuLogic.isValidPlacement(board, i / 9, i % 9, v)
                        board[i] = v
                        ok
                    }
                }
                boardView.wrong[i] = !correct
                if (!correct) wrongCount++
            }
        }
        tvSteps.text = if (wrongCount == 0) "No mistakes found." else "$wrongCount wrong cell(s) marked in red."
        boardView.invalidate()
    }

    /** The general solver: works for ANY Sudoku, not only generated ones. */
    private fun solveCurrent() {
        if (!SudokuLogic.isValidBoard(board)) {
            tvSteps.text = "Invalid puzzle: a number repeats in a row, column or box."
            toast("Invalid puzzle"); return
        }
        if (board.all { it != 0 }) { tvSteps.text = "Board is already full."; return }
        val count = SudokuLogic.countSolutions(board, 2)
        val (solved, steps) = SudokuLogic.solve(board)
        if (solved == null) {
            tvSteps.text = "No solution exists for this puzzle."
            toast("No solution"); return
        }
        val before = board.copyOf()
        board = solved
        givens = BooleanArray(81) { before[it] != 0 }
        boardView.notes = Array(81) { mutableSetOf() }
        boardView.wrong = BooleanArray(81)
        val uniqueNote = if (count == 1) "Unique solution." else "Multiple solutions exist — showing one of them."
        val log = steps.take(30).joinToString("\n") { "R${it.row + 1}C${it.col + 1} = ${it.value} — ${it.technique}" }
        val extra = if (steps.size > 30) "\n… and ${steps.size - 30} more steps." else ""
        tvSteps.text = "Solved in ${steps.size} steps. $uniqueNote\n$log$extra"
        // Copy solution to clipboard for sharing.
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Sudoku solution", SudokuLogic.toText(solved)))
        toast("Solved — solution copied")
        boardView.invalidate()
    }

    private fun importText() {
        val text = etImport.text.toString()
        val parsed = SudokuLogic.fromText(text)
        if (parsed == null) { toast("Need exactly 81 chars (digits, 0 or .)"); return }
        if (!SudokuLogic.isValidBoard(parsed)) { toast("Invalid puzzle: duplicate in a row/col/box"); return }
        board = parsed
        givens = BooleanArray(81) { board[it] != 0 }
        boardView.notes = Array(81) { mutableSetOf() }
        boardView.wrong = BooleanArray(81)
        tvSteps.text = "Imported. Press Solve to solve it, or play it from here."
        boardView.invalidate()
    }

    private fun savePuzzle() {
        if (!SudokuLogic.isValidBoard(board)) { toast("Invalid puzzle"); return }
        val diff = SudokuLogic.estimateDifficulty(board)
        prefs.edit().putString("created_${System.currentTimeMillis()}", SudokuLogic.toText(board)).apply()
        tvSteps.text = "Saved. Estimated difficulty: $diff. Givens: ${board.count { it != 0 }}. Unique solution: ${SudokuLogic.hasUniqueSolution(board)}"
        toast("Puzzle saved")
    }

    private fun recordWin() {
        val key = "won_${difficulty.name.lowercase()}"
        prefs.edit()
            .putInt("played_${difficulty.name.lowercase()}", prefs.getInt("played_${difficulty.name.lowercase()}", 0) + 1)
            .putInt(key, prefs.getInt(key, 0) + 1)
            .putInt("best_${difficulty.name.lowercase()}", minOf(prefs.getInt("best_${difficulty.name.lowercase()}", Int.MAX_VALUE), seconds).let { it })
            .apply()
        // Best time stored separately to avoid the minOf chain above overwriting incorrectly.
        val prevBest = prefs.getInt("best_${difficulty.name.lowercase()}", 0)
        if (prevBest == 0 || seconds < prevBest) prefs.edit().putInt("best_${difficulty.name.lowercase()}", seconds).apply()
    }

    private fun showStats() {
        val sb = StringBuilder("Stats\n")
        for (d in SudokuLogic.Difficulty.values()) {
            val k = d.name.lowercase()
            val played = prefs.getInt("played_$k", 0)
            val won = prefs.getInt("won_$k", 0)
            val best = prefs.getInt("best_$k", 0)
            sb.append("${d.label}: played $played, won $won")
            if (best > 0) sb.append(", best %02d:%02d".format(best / 60, best % 60))
            sb.append("\n")
        }
        tvStats.text = sb.toString()
    }

    private fun saveGame() {
        if (mode != Mode.PLAY) return
        prefs.edit()
            .putString("saved_board", SudokuLogic.toText(board))
            .putString("saved_puzzle", SudokuLogic.toText(puzzle))
            .putString("saved_solution", SudokuLogic.toText(solution))
            .putInt("saved_seconds", seconds)
            .apply()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onPause() { super.onPause(); saveGame(); timerRunning = false }
    override fun onResume() { super.onResume(); if (mode == Mode.PLAY) startTimer() }
}
