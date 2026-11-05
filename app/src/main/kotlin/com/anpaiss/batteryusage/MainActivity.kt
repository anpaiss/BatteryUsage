package com.anpaiss.batteryusage

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The page of the extension: the battery now, its chart over 24 h or 7 days with the rides,
 * what each ride used, and Android's own list of the battery used by each app.
 */
class MainActivity : Activity() {

    private lateinit var chart: BatteryChart
    private lateinit var now: TextView
    private lateinit var rate: TextView
    private lateinit var rides: LinearLayout
    private lateinit var day: TextView
    private lateinit var week: TextView
    private val main = Handler(Looper.getMainLooper())
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()

    private val refresh = object : Runnable {
        override fun run() { show(); main.postDelayed(this, 60_000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(BatteryChart.SURFACE)
        fun text(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }

        col.addView(text(15f, BatteryChart.TEXT_2).apply { text = "Battery" })
        now = text(34f, BatteryChart.TEXT, bold = true)
        rate = text(16f, BatteryChart.TEXT_2)
        col.addView(now); col.addView(rate)

        val range = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(4)) }
        fun choice(label: String, ms: Long) = TextView(this).apply {
            text = label; textSize = 16f; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { pick(ms) }
        }
        day = choice("24 h", 24 * 3600_000L)
        week = choice("7 days", BatteryLog.KEEP_MS)
        range.addView(day, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        range.addView(week, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        col.addView(range)

        chart = BatteryChart(this)
        col.addView(chart, LinearLayout.LayoutParams(MATCH_PARENT, dp(190)))
        col.addView(text(13f, BatteryChart.TEXT_2).apply {
            text = "Shaded: rides. Dashed: charging. A gap: the Karoo was off. Touch the line for the value."
            setPadding(0, dp(6), 0, 0)
        })

        col.addView(text(20f, BatteryChart.TEXT, bold = true).apply { text = "Rides"; setPadding(0, dp(20), 0, dp(4)) })
        rides = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(rides)

        col.addView(text(20f, BatteryChart.TEXT, bold = true).apply { text = "By app"; setPadding(0, dp(20), 0, dp(4)) })
        col.addView(text(15f, BatteryChart.TEXT_2).apply {
            text = "Android keeps its own list of the battery used by each app since the last full charge: " +
                "the screen, the GPS, the Karoo's apps and every extension. Apps that use very little are not listed. " +
                "The button opens Android's battery page: tap Battery usage there for the list."
        })
        col.addView(TextView(this).apply {
            text = "Battery use by app"; textSize = 16f; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
            setTextColor(BatteryChart.SURFACE); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(BatteryChart.TEXT) }
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
                    .onFailure { rate.text = "This Karoo does not open Android's battery page." }
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        col.addView(text(13f, BatteryChart.TEXT_2).apply {
            text = "The chart starts when this extension is installed and keeps seven days. It reads only what the " +
                "Karoo already reports about its battery: no permission, nothing sent anywhere."
            setPadding(0, dp(20), 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(col) })
        pick(24 * 3600_000L)
    }

    override fun onResume() { super.onResume(); main.post(refresh) }
    override fun onPause() { main.removeCallbacks(refresh); super.onPause() }

    private fun pick(ms: Long) {
        chart.rangeMs = ms
        // The chosen range filled, the other one outlined
        for ((v, on) in listOf(day to (ms == 24 * 3600_000L), week to (ms != 24 * 3600_000L))) {
            v.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                if (on) setColor(BatteryChart.TEXT) else { setColor(BatteryChart.SURFACE); setStroke(dp(1), BatteryChart.TEXT_2) }
            }
            v.setTextColor(if (on) BatteryChart.SURFACE else BatteryChart.TEXT)
            v.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun show() {
        val h = BatteryLog.read(this)
        chart.history = h
        val cur = BatteryLog.now(this)
        now.text = cur?.let { "${it.level} %" } ?: "–"
        rate.text = when {
            cur == null -> ""
            cur.plugged -> "Charging"
            else -> drainLastHour(h.samples, cur)?.let { "On battery: %.1f %% per hour in the last hour".format(it) }
                ?: "On battery"
        }

        rides.removeAllViews()
        val list = h.rides.filter { (it.end ?: System.currentTimeMillis()) - it.start >= 10 * 60_000L }.takeLast(5).reversed()
        if (list.isEmpty()) rides.addView(TextView(this).apply {
            textSize = 15f; setTextColor(BatteryChart.TEXT_2); text = "No ride recorded yet since the extension was installed."
        })
        for (r in list) rides.addView(TextView(this).apply {
            textSize = 15f; setTextColor(BatteryChart.TEXT); setPadding(0, dp(4), 0, dp(4))
            text = describe(r, h.samples)
        })
    }

    // Over the last hour on battery without a gap; nothing before 20 min, too few changes of level to mean anything
    private fun drainLastHour(s: List<BatteryLog.Sample>, cur: BatteryLog.Sample): Double? {
        val from = cur.t - 3600_000L
        var first = cur
        for (x in s.asReversed()) {
            if (x.t < from || x.plugged || first.t - x.t > BatteryLog.GAP_MS) break
            first = x
        }
        val hours = (cur.t - first.t) / 3600_000.0
        return if (hours < 1 / 3.0) null else (first.level - cur.level) / hours
    }

    private fun describe(r: BatteryLog.Ride, s: List<BatteryLog.Sample>): String {
        val end = r.end ?: System.currentTimeMillis()
        val inRide = s.filter { it.t in r.start..end }
        val when_ = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(r.start))
        val min = ((end - r.start) / 60_000).toInt()
        val dur = "%d h %02d".format(min / 60, min % 60)
        val ongoing = if (r.end == null) " (riding)" else ""
        val a = inRide.firstOrNull(); val b = inRide.lastOrNull()
        if (a == null || b == null || a === b) return "$when_ · $dur$ongoing"
        if (inRide.any { it.plugged }) return "$when_ · $dur$ongoing\ncharged during the ride"
        val perHour = (a.level - b.level) / ((b.t - a.t) / 3600_000.0)
        return "$when_ · $dur$ongoing\n${a.level} → ${b.level} %%, %.1f %% per hour".format(perHour)
    }
}
