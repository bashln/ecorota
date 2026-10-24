package app.ecorota.export

import app.ecorota.core.model.DrivingEvent
import app.ecorota.core.model.TripRecord
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Default exporter for the premium data export.
 *
 * CSV follows RFC 4180: UTF-8, comma separated, CRLF line endings, and a field
 * is quoted only when it contains a comma, a quote or a line break. Every
 * floating point column is rendered with exactly three decimals using
 * [Locale.US], so the output parses the same way in every locale. Timestamps are
 * ISO-8601 with the device's UTC offset; GPX timestamps are ISO-8601 in UTC.
 *
 * A trip contributes one row per event, repeating the trip columns on each row.
 * A trip without events contributes a single row whose event columns are empty,
 * so no recorded trip disappears from the export.
 *
 * GPX is a single track whose points carry the recorded speed in an extensions
 * element. GPX 1.1 forbids foreign elements in the GPX namespace, so the speed
 * lives in the `ecorota` namespace, in meters per second.
 *
 * The caller owns [OutputStream]s: this class flushes but never closes them.
 */
class DefaultTripExporter : TripExporter {

    override fun writeCsv(trips: List<TripRecord>, output: OutputStream) {
        val writer = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))
        writer.write(CSV_HEADER)
        writer.write(LINE_END)
        for (trip in trips) {
            if (trip.events.isEmpty()) {
                writer.write(csvRow(trip, null))
                writer.write(LINE_END)
            } else {
                for (event in trip.events) {
                    writer.write(csvRow(trip, event))
                    writer.write(LINE_END)
                }
            }
        }
        writer.flush()
    }

    override fun writeGpx(trip: TripRecord, output: OutputStream) {
        val writer = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))
        writer.write(XML_DECLARATION)
        writer.write(LINE_END)
        writer.write(
            """<gpx version="1.1" creator="$CREATOR" xmlns="$GPX_NAMESPACE" """ +
                """xmlns:ecorota="$EXTENSION_NAMESPACE" xmlns:xsi="$XSI_NAMESPACE" """ +
                "xsi:schemaLocation=\"$GPX_NAMESPACE $GPX_SCHEMA\">$LINE_END",
        )
        writer.write("  <metadata>$LINE_END")
        writer.write("    <time>${utcInstant(trip.startedAtMs)}</time>$LINE_END")
        writer.write("  </metadata>$LINE_END")
        writer.write("  <trk>$LINE_END")
        writer.write("    <name>${xmlText(trip.id)}</name>$LINE_END")
        writer.write("    <trkseg>$LINE_END")
        for (point in trip.route) {
            writer.write(
                "      <trkpt lat=\"${decimal(point.point.latitude, COORDINATE_DECIMALS)}\" " +
                    "lon=\"${decimal(point.point.longitude, COORDINATE_DECIMALS)}\">$LINE_END",
            )
            writer.write("        <time>${utcInstant(point.timestampMs)}</time>$LINE_END")
            writer.write("        <extensions>$LINE_END")
            writer.write("          <ecorota:speed>${decimal(point.speedKmh / MPS_TO_KMH)}</ecorota:speed>$LINE_END")
            writer.write("        </extensions>$LINE_END")
            writer.write("      </trkpt>$LINE_END")
        }
        writer.write("    </trkseg>$LINE_END")
        writer.write("  </trk>$LINE_END")
        writer.write("</gpx>$LINE_END")
        writer.flush()
    }

    override fun csvFileName(timestampMs: Long): String =
        "ecorota-trips-${fileStamp(timestampMs)}.csv"

    override fun gpxFileName(trip: TripRecord): String =
        "ecorota-trip-${fileStamp(trip.startedAtMs)}.gpx"

    private fun csvRow(trip: TripRecord, event: DrivingEvent?): String {
        val fields = ArrayList<String>(CSV_HEADER_FIELD_COUNT)
        fields += trip.id
        fields += trip.vehicleId
        fields += localIso(trip.startedAtMs)
        fields += localIso(trip.endedAtMs)
        fields += decimal(trip.distanceMeters / METERS_PER_KM)
        fields += decimal(trip.durationMs / MILLIS_PER_SECOND)
        fields += decimal(trip.movingDurationMs / MILLIS_PER_SECOND)
        fields += decimal(trip.idleDurationMs / MILLIS_PER_SECOND)
        fields += decimal(trip.averageSpeedKmh)
        fields += decimal(trip.maxSpeedKmh)
        fields += trip.score.toString()
        fields += trip.breakdown.behaviorScore.toString()
        fields += trip.breakdown.fuelScore.toString()
        fields += decimal(trip.fuel.estimatedLiters)
        fields += decimal(trip.fuel.avoidableLiters)
        fields += decimal(trip.fuel.avoidableCost)
        fields += trip.fuel.currency
        if (event != null) {
            fields += event.type.name
            fields += event.severity.name
            fields += localIso(event.startedAtMs)
            fields += decimal(event.durationMs / MILLIS_PER_SECOND)
            fields += decimal(event.peakValue)
            fields += decimal(event.extraFuelMilliliters)
        } else {
            repeat(CSV_EVENT_FIELD_COUNT) { fields += "" }
        }
        return fields.joinToString(",") { csvField(it) }
    }

    /** RFC 4180: quote when needed, doubling embedded quotes. */
    private fun csvField(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun xmlText(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    private fun localIso(timestampMs: Long): String =
        LOCAL_ISO.format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))

    private fun utcInstant(timestampMs: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timestampMs))

    private fun fileStamp(timestampMs: Long): String =
        FILE_STAMP.format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))

    private fun decimal(value: Double): String = decimal(value, DECIMALS)

    private fun decimal(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)

    private companion object {
        const val CREATOR = "EcoRota"
        const val LINE_END = "\r\n"
        const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
        const val GPX_SCHEMA = "http://www.topografix.com/GPX/1/1/gpx.xsd"
        const val XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance"
        const val EXTENSION_NAMESPACE = "https://ecorota.app/gpx/1"

        const val DECIMALS = 3
        const val COORDINATE_DECIMALS = 7
        const val MPS_TO_KMH = 3.6
        const val METERS_PER_KM = 1000.0
        const val MILLIS_PER_SECOND = 1000.0

        const val CSV_EVENT_FIELD_COUNT = 6
        const val CSV_HEADER_FIELD_COUNT = 23

        const val CSV_HEADER =
            "trip_id,vehicle_id,started_at_iso,ended_at_iso,distance_km,duration_s," +
                "moving_s,idle_s,avg_speed_kmh,max_speed_kmh,score,behavior_score,fuel_score," +
                "estimated_liters,avoidable_liters,avoidable_cost,currency,event_type," +
                "event_severity,event_started_at_iso,event_duration_s,event_peak," +
                "event_extra_fuel_ml"

        val LOCAL_ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US)
    }
}
