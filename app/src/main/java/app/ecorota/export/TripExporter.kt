package app.ecorota.export

import app.ecorota.core.model.TripRecord
import java.io.OutputStream

/**
 * Premium data export. CSV contains one row per event plus a trip row, GPX
 * contains the route as an open standard track.
 */
interface TripExporter {
    fun writeCsv(trips: List<TripRecord>, output: OutputStream)

    fun writeGpx(trip: TripRecord, output: OutputStream)

    fun csvFileName(timestampMs: Long): String

    fun gpxFileName(trip: TripRecord): String
}
