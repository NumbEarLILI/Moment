package com.example.moment.ui.place

internal class PlaceGeocodeRequestTracker {
    private var latestRequestId = 0L

    fun startRequest(): Long {
        latestRequestId += 1
        return latestRequestId
    }

    fun isLatest(request: Long): Boolean = request == latestRequestId

    fun canApply(
        request: Long,
        requestLatitude: Double,
        requestLongitude: Double,
        current: PlacePickUiState
    ): Boolean =
        isLatest(request) &&
            !current.placeNameUserLocked &&
            current.mapLat == requestLatitude &&
            current.mapLng == requestLongitude
}
