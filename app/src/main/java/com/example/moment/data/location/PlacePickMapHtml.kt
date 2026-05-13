package com.example.moment.data.location

import java.util.Locale

internal object PlacePickMapHtml {
    fun build(initialLat: Double, initialLng: Double): String {
        val lat = String.format(Locale.US, "%.6f", initialLat)
        val lng = String.format(Locale.US, "%.6f", initialLng)
        return """
            <!DOCTYPE html>
            <html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>html,body,#map{height:100%;margin:0;padding:0}</style>
            </head><body>
            <div id="map"></div>
            <script>
            var lat = $lat, lng = $lng;
            var map = L.map('map').setView([lat, lng], 15);
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '© OSM' }).addTo(map);
            var marker = L.marker([lat, lng], { draggable: true }).addTo(map);
            map.on('click', function(e) {
              marker.setLatLng(e.latlng);
            });
            function sendPick() {
              var p = marker.getLatLng();
              AndroidHost.onPick(p.lat, p.lng);
            }
            </script>
            </body></html>
        """.trimIndent()
    }
}
