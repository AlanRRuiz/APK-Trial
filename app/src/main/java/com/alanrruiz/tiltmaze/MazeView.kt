package com.alanrruiz.tiltmaze

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min
import kotlin.random.Random

/**
 * A tilt-controlled maze. The player rolls a ball through a randomly
 * generated maze by tilting the device; reaching the goal generates a
 * fresh maze and bumps the solved counter.
 */
class MazeView(context: Context) : View(context) {

    private companion object {
        const val COLS = 7
        const val ROWS = 11
        const val ACCEL_SCALE = 0.6f
        const val DAMPING = 0.92f
        const val MAX_SPEED = 18f
        const val HUD_HEIGHT = 120f
    }

    private class Cell {
        var top = true
        var right = true
        var bottom = true
        var left = true
        var visited = false
    }

    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F3460") }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E94560") }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2ECC71") }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EAEAEA")
        textSize = 42f
    }

    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var ballRadius = 0f
    private var goalRadius = 0f

    private var posX = 0f
    private var posY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var goalX = 0f
    private var goalY = 0f
    private var solvedCount = 0

    private var tiltX = 0f
    private var tiltY = 0f

    private val walls = mutableListOf<RectF>()
    private var running = false

    init {
        setBackgroundColor(Color.parseColor("#1A1A2E"))
    }

    fun onTilt(x: Float, y: Float) {
        tiltX = x
        tiltY = y
    }

    fun resume() {
        if (running) return
        running = true
        postOnAnimation(loop)
    }

    fun pause() {
        running = false
        removeCallbacks(loop)
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            update()
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val availableHeight = h - HUD_HEIGHT
        cellSize = min(w / COLS.toFloat(), availableHeight / ROWS.toFloat())
        offsetX = (w - cellSize * COLS) / 2f
        offsetY = HUD_HEIGHT + (availableHeight - cellSize * ROWS) / 2f
        ballRadius = cellSize * 0.28f
        goalRadius = cellSize * 0.32f
        newMaze()
    }

    private fun newMaze() {
        walls.clear()
        walls.addAll(buildWalls(generateMaze()))
        posX = cellSize * 0.5f
        posY = cellSize * 0.5f
        velocityX = 0f
        velocityY = 0f
        goalX = cellSize * (COLS - 0.5f)
        goalY = cellSize * (ROWS - 0.5f)
    }

    private fun generateMaze(): Array<Cell> {
        val cells = Array(COLS * ROWS) { Cell() }
        fun index(c: Int, r: Int) = r * COLS + c

        val stack = ArrayDeque<Pair<Int, Int>>()
        var current = 0 to 0
        cells[index(0, 0)].visited = true
        stack.addLast(current)

        while (stack.isNotEmpty()) {
            current = stack.last()
            val (c, r) = current
            val neighbors = mutableListOf<Triple<Int, Int, Int>>() // dc, dr, direction 0=up 1=right 2=down 3=left
            if (r > 0 && !cells[index(c, r - 1)].visited) neighbors.add(Triple(0, -1, 0))
            if (c < COLS - 1 && !cells[index(c + 1, r)].visited) neighbors.add(Triple(1, 0, 1))
            if (r < ROWS - 1 && !cells[index(c, r + 1)].visited) neighbors.add(Triple(0, 1, 2))
            if (c > 0 && !cells[index(c - 1, r)].visited) neighbors.add(Triple(-1, 0, 3))

            if (neighbors.isEmpty()) {
                stack.removeLast()
                continue
            }

            val (dc, dr, dir) = neighbors[Random.nextInt(neighbors.size)]
            val nc = c + dc
            val nr = r + dr
            val currentCell = cells[index(c, r)]
            val neighborCell = cells[index(nc, nr)]
            when (dir) {
                0 -> { currentCell.top = false; neighborCell.bottom = false }
                1 -> { currentCell.right = false; neighborCell.left = false }
                2 -> { currentCell.bottom = false; neighborCell.top = false }
                3 -> { currentCell.left = false; neighborCell.right = false }
            }
            neighborCell.visited = true
            stack.addLast(nc to nr)
        }
        return cells
    }

    private fun buildWalls(cells: Array<Cell>): List<RectF> {
        val half = cellSize * 0.09f
        val result = mutableListOf<RectF>()
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val cell = cells[r * COLS + c]
                val x0 = c * cellSize
                val y0 = r * cellSize
                if (cell.top) result.add(RectF(x0 - half, y0 - half, x0 + cellSize + half, y0 + half))
                if (cell.left) result.add(RectF(x0 - half, y0 - half, x0 + half, y0 + cellSize + half))
                if (r == ROWS - 1 && cell.bottom) {
                    result.add(RectF(x0 - half, y0 + cellSize - half, x0 + cellSize + half, y0 + cellSize + half))
                }
                if (c == COLS - 1 && cell.right) {
                    result.add(RectF(x0 + cellSize - half, y0 - half, x0 + cellSize + half, y0 + cellSize + half))
                }
            }
        }
        return result
    }

    private fun circleHitsWalls(cx: Float, cy: Float): Boolean {
        for (wall in walls) {
            val closestX = cx.coerceIn(wall.left, wall.right)
            val closestY = cy.coerceIn(wall.top, wall.bottom)
            val dx = cx - closestX
            val dy = cy - closestY
            if (dx * dx + dy * dy < ballRadius * ballRadius) return true
        }
        return false
    }

    private fun update() {
        if (cellSize <= 0f) return

        velocityX = (velocityX - tiltX * ACCEL_SCALE) * DAMPING
        velocityY = (velocityY + tiltY * ACCEL_SCALE) * DAMPING
        velocityX = velocityX.coerceIn(-MAX_SPEED, MAX_SPEED)
        velocityY = velocityY.coerceIn(-MAX_SPEED, MAX_SPEED)

        val newX = posX + velocityX
        if (!circleHitsWalls(newX, posY)) posX = newX else velocityX = 0f

        val newY = posY + velocityY
        if (!circleHitsWalls(posX, newY)) posY = newY else velocityY = 0f

        val dx = posX - goalX
        val dy = posY - goalY
        if (dx * dx + dy * dy < goalRadius * goalRadius) {
            solvedCount++
            newMaze()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellSize <= 0f) return

        canvas.save()
        canvas.translate(offsetX, offsetY)
        for (wall in walls) canvas.drawRect(wall, wallPaint)
        canvas.drawCircle(goalX, goalY, goalRadius, goalPaint)
        canvas.drawCircle(posX, posY, ballRadius, ballPaint)
        canvas.restore()

        canvas.drawText("Solved: $solvedCount", offsetX, HUD_HEIGHT * 0.65f, hudPaint)
    }
}
