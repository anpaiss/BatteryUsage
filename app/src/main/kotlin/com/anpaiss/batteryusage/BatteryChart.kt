package com.anpaiss.batteryusage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The battery level over the last [rangeMs], one line: solid on battery, dashed while charging,
 * broken where the Karoo was off. Rides are shaded bands behind it. Touch shows the value at that time.
 */
@SuppressLint("ViewConstructor")
class BatteryChart(ctx: Context) : View(ctx) {

    companion object {
        const val SURFACE = 0xFFFCFCFB.toInt()
        const val TEXT = 0xFF0B0B0B.toInt()
        const val TEXT_2 = 0xFF52514E.toInt()
        const val GRID = 0xFFE4E2DC.toInt()
        const val SERIES = 0xFFEB6834.toInt()
        const val BAND = 0x1F52514E
    }

    var history = BatteryLog.History(emptyList(), emptyList())
        set(v) { field = v; invalidate() }
    var rangeMs = 24 * 3600_000L
        set(v) { field = v; touchX = null; invalidate() }

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); color = SERIES
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val dashed = Paint(line).apply { pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f) }
    private val grid = Paint().apply { color = GRID; strokeWidth = dp(1f) }
    private val band = Paint().apply { color = BAND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_2; textSize = dp(12f) }
    private val cross = Paint().apply { color = TEXT_2; strokeWidth = dp(1f) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SERIES }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE }
    private val tipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT }
    private val tipText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE; textSize = dp(13f) }
    private var touchX: Float? = null

    private val plot = RectF()
    private var end = 0L

    private fun x(t: Long) = plot.left + (t - (end - rangeMs)).toFloat() / rangeMs * plot.width()
    private fun y(level: Int) = plot.bottom - level / 100f * plot.height()
    private fun time(px: Float) = end - rangeMs + ((px - plot.left) / plot.width() * rangeMs).toLong()

    override fun onDraw(c: Canvas) {
        c.drawColor(SURFACE)
        end = System.currentTimeMillis()
        plot.set(dp(36f), dp(10f), width - dp(10f), height - dp(26f))
        val start = end - rangeMs

        for (r in history.rides) {
            val a = maxOf(r.start, start); val b = minOf(r.end ?: end, end)
            if (b > a) c.drawRect(x(a), plot.top, x(b), plot.bottom, band)
        }
        for (lv in 0..100 step 25) {
            c.drawLine(plot.left, y(lv), plot.right, y(lv), grid)
            if (lv % 50 == 0) c.drawText("$lv %", 0f, y(lv) + dp(4f), label)
        }
        drawTimeAxis(c, start)

        // One path for each run without a gap, split where the charger is plugged in or out
        val s = history.samples.filter { it.t >= start - BatteryLog.GAP_MS }
        var i = 0
        while (i < s.size - 1) {
            val p = Path(); val plugged = s[i].plugged
            p.moveTo(x(s[i].t), y(s[i].level))
            var j = i
            while (j < s.size - 1 && s[j + 1].t - s[j].t <= BatteryLog.GAP_MS) {
                p.lineTo(x(s[j + 1].t), y(s[j + 1].level)); j++
                if (s[j].plugged != plugged) break
            }
            if (j > i) { c.save(); c.clipRect(plot); c.drawPath(p, if (plugged) dashed else line); c.restore() }
            i = if (j > i) j else i + 1
        }

        touchX?.let { tx -> drawTouch(c, tx.coerceIn(plot.left, plot.right), s) }
    }

    private fun drawTimeAxis(c: Canvas, start: Long) {
        val day = rangeMs > 24 * 3600_000L
        val fmt = SimpleDateFormat(if (day) "EEE" else "HH:mm", Locale.getDefault())
        val cal = Calendar.getInstance().apply { timeInMillis = start; set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (day) cal.set(Calendar.HOUR_OF_DAY, 0) else cal.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY) / 6 * 6)
        while (cal.timeInMillis <= end) {
            val t = cal.timeInMillis
            if (t >= start) {
                val px = x(t)
                c.drawLine(px, plot.bottom, px, plot.bottom + dp(4f), cross)
                // A day label sits in the middle of its day, an hour label on its tick
                val text = fmt.format(Date(t))
                val cx = if (day) px + plot.width() / 7 / 2 else px
                if (cx < plot.right) c.drawText(text, (cx - label.measureText(text) / 2).coerceIn(0f, width - label.measureText(text)), height - dp(6f), label)
            }
            cal.add(if (day) Calendar.DAY_OF_MONTH else Calendar.HOUR_OF_DAY, if (day) 1 else 6)
        }
    }

    private fun drawTouch(c: Canvas, tx: Float, s: List<BatteryLog.Sample>) {
        val t = time(tx)
        // The value is the nearest sample in the same run, none in a gap
        val near = s.minByOrNull { kotlin.math.abs(it.t - t) } ?: return
        if (kotlin.math.abs(near.t - t) > BatteryLog.GAP_MS) return
        val px = x(near.t); val py = y(near.level)
        c.drawLine(px, plot.top, px, plot.bottom, cross)
        c.drawCircle(px, py, dp(6f), ring); c.drawCircle(px, py, dp(4f), dot)
        val ride = history.rides.any { near.t >= it.start && near.t <= (it.end ?: end) }
        val text = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(near.t)) +
            "  ${near.level} %" + when { near.plugged -> "  charging"; ride -> "  riding"; else -> "" }
        val w = tipText.measureText(text) + dp(16f); val h = dp(26f)
        val left = (px - w / 2).coerceIn(plot.left, plot.right - w)
        val top = if (py - h - dp(12f) > plot.top) py - h - dp(12f) else py + dp(12f)
        c.drawRoundRect(left, top, left + w, top + h, dp(4f), dp(4f), tipBg)
        c.drawText(text, left + dp(8f), top + h - dp(8f), tipText)
    }

    // A tap or a sideways drag moves the value along the line; a vertical drag is left to the page, to scroll
    private var downX = 0f
    private var downY = 0f
    private var sideways = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; sideways = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(e.x - downX); val dy = kotlin.math.abs(e.y - downY)
                if (!sideways && dy > dx && dy > dp(8f)) return false
                if (!sideways && dx > dp(8f)) { sideways = true; parent.requestDisallowInterceptTouchEvent(true) }
                if (sideways) { touchX = e.x; invalidate() }
            }
            // The value stays on screen after the finger lifts, until the next touch
            MotionEvent.ACTION_UP -> if (!sideways) { touchX = e.x; invalidate() }
        }
        return true
    }
}
