package com.mthtamim.sudoku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Draws the 9x9 board: thick 3x3 lines, selection, same-number + peer highlight, notes. */
class SudokuBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var board: IntArray = IntArray(81)
    var givens: BooleanArray = BooleanArray(81)
    var notes: Array<MutableSet<Int>> = Array(81) { mutableSetOf() }
    var wrong: BooleanArray = BooleanArray(81)
    var selected = -1
    var onCellSelected: ((Int) -> Unit)? = null

    private val thin = Paint().apply { color = Color.parseColor("#334155"); strokeWidth = 2f }
    private val thick = Paint().apply { color = Color.parseColor("#94A3B8"); strokeWidth = 5f }
    private val selPaint = Paint().apply { color = Color.parseColor("#1E3A5F") }
    private val peerPaint = Paint().apply { color = Color.parseColor("#16233A") }
    private val samePaint = Paint().apply { color = Color.parseColor("#1B3A2A") }
    private val givenPaint = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val userPaint = Paint().apply { color = Color.parseColor("#38BDF8"); textAlign = Paint.Align.CENTER }
    private val wrongPaint = Paint().apply { color = Color.parseColor("#F43F5E"); textAlign = Paint.Align.CENTER }
    private val notePaint = Paint().apply { color = Color.parseColor("#94A3B8"); textAlign = Paint.Align.CENTER }

    override fun onMeasure(w: Int, h: Int) {
        val s = min(MeasureSpec.getSize(w), MeasureSpec.getSize(h))
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        val cell = width / 9f
        givenPaint.textSize = cell * 0.55f
        userPaint.textSize = cell * 0.55f
        wrongPaint.textSize = cell * 0.55f
        notePaint.textSize = cell * 0.22f
        val selVal = if (selected in 0..80) board[selected] else 0
        for (i in 0 until 81) {
            val r = i / 9; val c = i % 9
            val x = c * cell; val y = r * cell
            val isPeer = selected in 0..80 && (r == selected / 9 || c == selected % 9 ||
                (r / 3 == (selected / 9) / 3 && c / 3 == (selected % 9) / 3))
            when {
                i == selected -> canvas.drawRect(x, y, x + cell, y + cell, selPaint)
                selVal != 0 && board[i] == selVal -> canvas.drawRect(x, y, x + cell, y + cell, samePaint)
                isPeer -> canvas.drawRect(x, y, x + cell, y + cell, peerPaint)
            }
            val v = board[i]
            if (v != 0) {
                val paint = when { wrong[i] -> wrongPaint; givens[i] -> givenPaint; else -> userPaint }
                canvas.drawText(v.toString(), x + cell / 2, y + cell / 2 + paint.textSize * 0.35f, paint)
            } else if (notes[i].isNotEmpty()) {
                for (n in notes[i]) {
                    val nr = (n - 1) / 3; val nc = (n - 1) % 3
                    canvas.drawText(n.toString(), x + cell * (nc + 0.5f) / 3, y + cell * (nr + 0.5f) / 3 + notePaint.textSize * 0.35f, notePaint)
                }
            }
        }
        for (k in 0..9) {
            val p = if (k % 3 == 0) thick else thin
            canvas.drawLine(k * cell, 0f, k * cell, height.toFloat(), p)
            canvas.drawLine(0f, k * cell, width.toFloat(), k * cell, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) {
            val cell = width / 9f
            val c = (e.x / cell).toInt().coerceIn(0, 8)
            val r = (e.y / cell).toInt().coerceIn(0, 8)
            selected = r * 9 + c
            onCellSelected?.invoke(selected)
            invalidate()
            return true
        }
        return super.onTouchEvent(e)
    }
}
