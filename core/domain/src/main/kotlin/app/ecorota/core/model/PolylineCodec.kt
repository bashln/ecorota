package app.ecorota.core.model

/**
 * Google's encoded polyline format. It keeps trip routes small enough to store
 * in a single Room column while remaining readable by common map tooling.
 */
object PolylineCodec {
    private const val PRECISION = 1e5

    fun encode(points: List<GeoPoint>): String {
        if (points.isEmpty()) return ""
        val result = StringBuilder()
        var previousLat = 0
        var previousLon = 0
        for (point in points) {
            val lat = Math.round(point.latitude * PRECISION).toInt()
            val lon = Math.round(point.longitude * PRECISION).toInt()
            encodeValue(lat - previousLat, result)
            encodeValue(lon - previousLon, result)
            previousLat = lat
            previousLon = lon
        }
        return result.toString()
    }

    fun decode(encoded: String): List<GeoPoint> {
        if (encoded.isEmpty()) return emptyList()
        val points = ArrayList<GeoPoint>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            val latResult = decodeValue(encoded, index)
            lat += latResult.first
            index = latResult.second
            val lonResult = decodeValue(encoded, index)
            lon += lonResult.first
            index = lonResult.second
            points += GeoPoint(lat / PRECISION, lon / PRECISION)
        }
        return points
    }

    private fun encodeValue(value: Int, out: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        out.append((v + 63).toChar())
    }

    private fun decodeValue(encoded: String, start: Int): Pair<Int, Int> {
        var index = start
        var shift = 0
        var result = 0
        while (true) {
            val byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
            if (byte < 0x20) break
        }
        val value = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        return value to index
    }
}
