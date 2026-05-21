package com.example.moment.ui.place

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceGeocodeRequestTrackerTest {

    @Test
    fun olderRequest_isStaleAfterNewerRequestStarts() {
        val tracker = PlaceGeocodeRequestTracker()

        val first = tracker.startRequest()
        val second = tracker.startRequest()

        assertFalse(tracker.isLatest(first))
        assertTrue(tracker.isLatest(second))
    }

    @Test
    fun latestRequest_canApplyOnlyWhenNameIsNotUserLockedAndCoordinatesStillMatch() {
        val tracker = PlaceGeocodeRequestTracker()
        val request = tracker.startRequest()

        assertTrue(
            tracker.canApply(
                request = request,
                requestLatitude = 39.9,
                requestLongitude = 116.4,
                current = PlacePickUiState(
                    mapLat = 39.9,
                    mapLng = 116.4,
                    placeNameUserLocked = false
                )
            )
        )
        assertFalse(
            tracker.canApply(
                request = request,
                requestLatitude = 39.9,
                requestLongitude = 116.4,
                current = PlacePickUiState(
                    mapLat = 39.9,
                    mapLng = 116.4,
                    placeNameUserLocked = true
                )
            )
        )
        assertFalse(
            tracker.canApply(
                request = request,
                requestLatitude = 39.9,
                requestLongitude = 116.4,
                current = PlacePickUiState(
                    mapLat = 31.2,
                    mapLng = 121.5,
                    placeNameUserLocked = false
                )
            )
        )
    }
}
