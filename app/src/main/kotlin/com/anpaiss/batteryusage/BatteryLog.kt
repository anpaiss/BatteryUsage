package com.anpaiss.batteryusage

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

/**
 * The battery history the extension keeps, one line per event in files/battery.csv:
 * `time,B,level,plugged` for the battery, `time,R,1` and `time,R,0` for a ride starting and ending.
 * Seven days are kept; the page reads the same file.
 */
object BatteryLog {

    const val KEEP_MS = 7 * 24 * 3600_000L

    data class Sample(val t: Long, val level: Int, val plugged: Boolean)
    data class Ride(val start: Long, val end: Long?)
    data class History(val samples: List<Sample>, val rides: List<Ride>)

    private fun file(ctx: Context) = File(ctx.filesDir, "battery.csv")

    /** The battery now, from the sticky broadcast: no receiver and no permission needed. */
    fun now(ctx: Context): Sample? {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        return Sample(System.currentTimeMillis(), level * 100 / scale, i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
    }

    @Synchronized fun battery(ctx: Context, s: Sample) = append(ctx, "${s.t},B,${s.level},${if (s.plugged) 1 else 0}")

    @Synchronized fun ride(ctx: Context, riding: Boolean) = append(ctx, "${System.currentTimeMillis()},R,${if (riding) 1 else 0}")

    private fun append(ctx: Context, line: String) {
        val f = file(ctx)
        f.appendText(line + "\n")
        // Once a day the file is cut back to the last seven days
        if (f.length() > 64_000 && System.currentTimeMillis() - (ctx.getSharedPreferences("log", 0).getLong("pruned", 0)) > 24 * 3600_000L) {
            val from = System.currentTimeMillis() - KEEP_MS
            f.writeText(f.readLines().filter { (it.substringBefore(',').toLongOrNull() ?: 0) >= from }.joinToString("\n", postfix = "\n"))
            ctx.getSharedPreferences("log", 0).edit().putLong("pruned", System.currentTimeMillis()).apply()
        }
    }

    /** Two samples further apart than this mean the Karoo was off in between. */
    const val GAP_MS = 25 * 60_000L

    @Synchronized fun read(ctx: Context): History {
        val samples = mutableListOf<Sample>()
        val marks = mutableListOf<Pair<Long, Boolean>>()
        val f = file(ctx)
        if (f.exists()) for (line in f.readLines()) {
            val p = line.split(',')
            val t = p.getOrNull(0)?.toLongOrNull() ?: continue
            when (p.getOrNull(1)) {
                "B" -> samples += Sample(t, p.getOrNull(2)?.toIntOrNull() ?: continue, p.getOrNull(3) == "1")
                "R" -> marks += t to (p.getOrNull(2) == "1")
            }
        }
        // A ride ends at its end mark, or earlier where the Karoo went off: the time it was off is not riding
        fun cut(start: Long, end: Long?): Long? {
            var prev: Long? = null
            for (s in samples) {
                if (s.t < start) { prev = s.t; continue }
                if (end != null && s.t > end) break
                if (prev != null && prev >= start && s.t - prev > GAP_MS) return prev
                prev = s.t
            }
            return end
        }
        val rides = mutableListOf<Ride>()
        var open: Long? = null
        for ((t, on) in marks) {
            if (on) { open?.let { rides += Ride(it, cut(it, t)) }; open = t }
            else { open?.let { rides += Ride(it, cut(it, t)) }; open = null }
        }
        open?.let { rides += Ride(it, cut(it, null)) }
        return History(samples, rides)
    }
}
