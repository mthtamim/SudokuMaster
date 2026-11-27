package com.mthtamim.sudoku

import kotlin.random.Random

/** Pure Sudoku logic — no Android dependency, so it can be unit-tested. */
object SudokuLogic {
    const val SIZE = 9
    fun idx(r: Int, c: Int) = r * 9 + c
    fun rowOf(i: Int) = i / 9
    fun colOf(i: Int) = i % 9
    fun boxOf(r: Int, c: Int) = (r / 3) * 3 + (c / 3)

    fun isValidPlacement(board: IntArray, r: Int, c: Int, v: Int): Boolean {
        if (v !in 1..9) return false
        for (k in 0 until 9) {
            if (board[idx(r, k)] == v) return false
            if (board[idx(k, c)] == v) return false
        }
        val br = (r / 3) * 3; val bc = (c / 3) * 3
        for (dr in 0 until 3) for (dc in 0 until 3)
            if (board[idx(br + dr, bc + dc)] == v) return false
        return true
    }

    /** True if the givens themselves contain no duplicate in any row/col/box. */
    fun isValidBoard(board: IntArray): Boolean {
        for (r in 0 until 9) for (c in 0 until 9) {
            val v = board[idx(r, c)]
            if (v != 0) {
                board[idx(r, c)] = 0
                val ok = isValidPlacement(board, r, c, v)
                board[idx(r, c)] = v
                if (!ok) return false
            }
        }
        return true
    }

    fun candidates(board: IntArray, r: Int, c: Int): List<Int> {
        if (board[idx(r, c)] != 0) return emptyList()
        return (1..9).filter { isValidPlacement(board, r, c, it) }
    }

    data class Step(val row: Int, val col: Int, val value: Int, val technique: String)

    /** General solver: logical singles first, then backtracking. Works for ANY valid Sudoku. */
    fun solve(board: IntArray): Pair<IntArray?, List<Step>> {
        val work = board.copyOf()
        val steps = mutableListOf<Step>()
        // Logical pass: repeat Naked Single / Hidden Single until stuck.
        var progressed = true
        while (progressed) {
            progressed = false
            for (r in 0 until 9) for (c in 0 until 9) {
                if (work[idx(r, c)] == 0) {
                    val cand = candidates(work, r, c)
                    if (cand.size == 1) {
                        work[idx(r, c)] = cand[0]
                        steps.add(Step(r, c, cand[0], "Naked Single"))
                        progressed = true
                    }
                }
            }
            if (!progressed) {
                // Hidden Single: a value that fits in only one cell of a unit.
                outer@ for (unit in 0 until 27) {
                    val cells = unitCells(unit)
                    for (v in 1..9) {
                        val places = cells.filter { work[it] == 0 && isValidPlacement(work, rowOf(it), colOf(it), v) }
                        if (places.size == 1) {
                            val i = places[0]
                            work[i] = v
                            steps.add(Step(rowOf(i), colOf(i), v, "Hidden Single"))
                            progressed = true
                            break@outer
                        }
                    }
                }
            }
        }
        if (work.all { it != 0 }) return work to steps
        // Backtracking for the rest (covers Hard/Expert puzzles needing guesses).
        val solved = work.copyOf()
        if (backtrack(solved, steps)) return solved to steps
        return null to steps
    }

    private fun backtrack(board: IntArray, steps: MutableList<Step>): Boolean {
        // Pick empty cell with fewest candidates (MRV).
        var best = -1; var bestCand: List<Int> = emptyList()
        for (i in 0 until 81) {
            if (board[i] == 0) {
                val cand = candidates(board, rowOf(i), colOf(i))
                if (cand.isEmpty()) return false
                if (best == -1 || cand.size < bestCand.size) { best = i; bestCand = cand }
                if (cand.size == 1) break
            }
        }
        if (best == -1) return true
        val r = rowOf(best); val c = colOf(best)
        for (v in bestCand) {
            board[best] = v
            val mark = steps.size
            steps.add(Step(r, c, v, "Trial / Backtracking"))
            if (backtrack(board, steps)) return true
            steps.subList(mark, steps.size).clear()
            board[best] = 0
        }
        return false
    }

    private fun unitCells(unit: Int): List<Int> {
        return when {
            unit < 9 -> (0 until 9).map { idx(unit, it) } // row
            unit < 18 -> (0 until 9).map { idx(it, unit - 9) } // col
            else -> { val b = unit - 18; val br = (b / 3) * 3; val bc = (b % 3) * 3
                (0 until 3).flatMap { dr -> (0 until 3).map { dc -> idx(br + dr, bc + dc) } } }
        }
    }

    /** Count solutions up to [limit]. 0 = none, 1 = unique, >=2 = multiple. */
    fun countSolutions(board: IntArray, limit: Int = 2): Int {
        var count = 0
        fun search(b: IntArray): Boolean {
            var empty = -1
            for (i in 0 until 81) if (b[i] == 0) { empty = i; break }
            if (empty == -1) { count++; return count >= limit }
            for (v in candidates(b, rowOf(empty), colOf(empty))) {
                b[empty] = v
                if (search(b)) { b[empty] = 0; return true }
                b[empty] = 0
            }
            return false
        }
        search(board.copyOf())
        return count
    }

    fun hasUniqueSolution(board: IntArray) = countSolutions(board, 2) == 1

    /** Full solved board, shuffled. */
    fun fullSolution(random: Random): IntArray {
        val board = IntArray(81)
        fun fill(i: Int): Boolean {
            if (i == 81) return true
            val r = rowOf(i); val c = colOf(i)
            for (v in (1..9).shuffled(random)) {
                if (isValidPlacement(board, r, c, v)) {
                    board[i] = v
                    if (fill(i + 1)) return true
                    board[i] = 0
                }
            }
            return false
        }
        fill(0)
        return board
    }

    enum class Difficulty(val givens: Int, val label: String) {
        EASY(40, "Easy"), MEDIUM(34, "Medium"), HARD(30, "Hard"), EXPERT(26, "Expert")
    }

    /** Generate a puzzle with a unique solution. Givens are removed symmetrically. */
    fun generate(diff: Difficulty, random: Random = Random(System.currentTimeMillis())): Pair<IntArray, IntArray> {
        val solution = fullSolution(random)
        val puzzle = solution.copyOf()
        val order = (0 until 81).shuffled(random)
        var removed = 0
        val targetRemove = 81 - diff.givens
        for (i in order) {
            if (removed >= targetRemove) break
            val mirror = 80 - i
            if (puzzle[i] == 0) continue
            val save = puzzle[i]; val saveM = puzzle[mirror]
            puzzle[i] = 0; puzzle[mirror] = 0
            if (hasUniqueSolution(puzzle)) {
                removed += if (mirror == i) 1 else 2
            } else {
                puzzle[i] = save; puzzle[mirror] = saveM
            }
        }
        return puzzle to solution
    }

    /** Rough difficulty estimate for a created puzzle. */
    fun estimateDifficulty(board: IntArray): String {
        val givens = board.count { it != 0 }
        val (_, steps) = solve(board)
        val usedTrial = steps.any { it.technique.startsWith("Trial") }
        return when {
            !hasUniqueSolution(board) -> "Not unique"
            usedTrial -> if (givens <= 28) "Expert" else "Hard"
            givens >= 38 -> "Easy"
            givens >= 32 -> "Medium"
            else -> "Hard"
        }
    }

    fun toText(board: IntArray): String =
        board.joinToString("") { if (it == 0) "." else it.toString() }

    fun fromText(text: String): IntArray? {
        val clean = text.filter { it.isDigit() || it == '.' }
        if (clean.length != 81) return null
        return IntArray(81) { if (clean[it] == '.') 0 else clean[it].digitToInt() }
    }
}
