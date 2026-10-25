package app.ecorota.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Locale-aware date helpers shared by the trip lists and the detail screen. */
object DateFormats {

    fun dayHeader(epochDay: Long, locale: Locale): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val formatted = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        return formatted.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
    }

    fun shortDay(epochDay: Long, locale: Locale): String =
        LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofPattern("EEE", locale))
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(locale) else char.toString()
            }

    fun monthDay(epochDay: Long, locale: Locale): String =
        LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofPattern("d MMM", locale))

    fun time(epochMillis: Long, locale: Locale): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    fun dateTime(epochMillis: Long, locale: Locale): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(locale),
            )
}
