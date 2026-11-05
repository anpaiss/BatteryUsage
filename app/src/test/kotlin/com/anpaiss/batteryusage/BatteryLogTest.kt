package com.anpaiss.batteryusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryLogTest {

    private val min = 60_000L

    // A sample every 10 min from `from` to `to` (minutes), the level falling one point each
    private fun samples(from: Int, to: Int, level: Int) =
        (from..to step 10).mapIndexed { i, m -> "${m * min},B,${level - i},0" }

    @Test fun rideEndsAtItsEndMark() {
        val h = BatteryLog.parse(samples(0, 120, 90) + listOf("${10 * min},R,1", "${100 * min},R,0"))
        assertEquals(1, h.rides.size)
        assertEquals(10 * min, h.rides[0].start)
        assertEquals(100 * min, h.rides[0].end)
    }

    @Test fun rideStillGoingHasNoEnd() {
        val h = BatteryLog.parse(samples(0, 60, 90) + listOf("${10 * min},R,1"))
        assertNull(h.rides[0].end)
    }

    @Test fun karooSwitchedOffEndsTheRideAtTheLastSampleBeforeTheGap() {
        // Riding from 10 to 50 min, Karoo off until 200, the end mark only at 210 after the restart
        val lines = samples(0, 50, 90) + samples(200, 230, 80) + listOf("${10 * min},R,1", "${210 * min},R,0")
        val h = BatteryLog.parse(lines.sortedBy { it.substringBefore(',').toLong() })
        assertEquals(50 * min, h.rides[0].end)
    }

    @Test fun aNewStartClosesTheRideThatWasNeverEnded() {
        val lines = samples(0, 50, 90) + samples(200, 260, 80) + listOf("${10 * min},R,1", "${210 * min},R,1", "${250 * min},R,0")
        val h = BatteryLog.parse(lines.sortedBy { it.substringBefore(',').toLong() })
        assertEquals(2, h.rides.size)
        assertEquals(50 * min, h.rides[0].end)
        assertEquals(210 * min, h.rides[1].start)
        assertEquals(250 * min, h.rides[1].end)
    }

    @Test fun brokenLinesAreSkipped() {
        val h = BatteryLog.parse(listOf("", "x,B,1,0", "${min},B,,0", "${2 * min},B,77,1", "${3 * min},Q,1"))
        assertEquals(listOf(BatteryLog.Sample(2 * min, 77, true)), h.samples)
    }
}
