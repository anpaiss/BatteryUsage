package com.anpaiss.batteryusage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.RideState

/**
 * Keeps the battery history: a line when the level or the charger changes, one every
 * 10 min when nothing does (so a gap in the chart means the Karoo was off), and the rides.
 * It only listens to broadcasts the system sends anyway: no polling of its own beyond the 10 min.
 */
class BatteryExtension : KarooExtension("batteryusage", BuildConfig.VERSION_NAME) {

    private lateinit var karoo: KarooSystemService
    private val main = Handler(Looper.getMainLooper())
    private var last: BatteryLog.Sample? = null
    private var riding: Boolean? = null

    private val changed = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = record(force = false)
    }

    private val tick = object : Runnable {
        override fun run() { record(force = true); main.postDelayed(this, 10 * 60_000L) }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(changed, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        main.postDelayed(tick, 10 * 60_000L)
        karoo = KarooSystemService(applicationContext)
        karoo.connect { ok ->
            if (ok) karoo.addConsumer(RideState.Params, { _ -> }, {}) { s: RideState ->
                val now = s !is RideState.Idle
                // The first state after a start is only a mark when a ride is already going
                if (now != riding) { val first = riding == null; riding = now; if (!first || now) main.post { BatteryLog.ride(this, now) } }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(changed) }
        main.removeCallbacks(tick)
        if (riding == true) BatteryLog.ride(this, false)
        karoo.disconnect()
        super.onDestroy()
    }

    private fun record(force: Boolean) {
        val s = BatteryLog.now(this) ?: return
        val l = last
        if (!force && l != null && l.level == s.level && l.plugged == s.plugged) return
        last = s
        BatteryLog.battery(this, s)
    }
}
