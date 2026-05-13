package com.example.moment.domain.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides the [Instant] stored on a newly created fragment when the user picked a calendar day
 * from the timeline (e.g. backfilling another day).
 *
 * - No anchor date: `null` → caller should persist the current instant from [Clock].
 * - Anchor is **today** in [zoneId]: `null` → same as above (real save time, not fixed noon).
 * - Anchor is another day: combine that date with the **current local time of day** in [zoneId]
 *   so the entry sorts on the intended day while still reflecting when it was written.
 */
fun resolveNewFragmentRecordedAt(
    clock: Clock,
    zoneId: ZoneId,
    anchorCalendarDate: LocalDate?
): Instant? {
    val target = anchorCalendarDate ?: return null
    val nowZoned = clock.instant().atZone(zoneId)
    return if (target == nowZoned.toLocalDate()) {
        null
    } else {
        target.atTime(nowZoned.toLocalTime()).atZone(zoneId).toInstant()
    }
}
