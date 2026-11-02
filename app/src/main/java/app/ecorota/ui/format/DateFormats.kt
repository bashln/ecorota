package app.ecorota.ui.format

import android.text.format.DateFormat
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
        val pattern = bestPattern(locale, "EEEEMMMMd")
        val formatter = when {
            pattern != null -> DateTimeFormatter.ofPattern(pattern, locale)
            locale.language.equals("pt", ignoreCase = true) ->
                DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale)
            locale.language.equals("en", ignoreCase = true) ->
                DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        }
        return date.format(formatter).replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
    }

    fun shortDay(epochDay: Long, locale: Locale): String =
        LocalDate.ofEpochDay(epochDay)
            .format(DateTimeFormatter.ofPattern("EEE", locale))
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(locale) else char.toString()
            }

    fun monthDay(epochDay: Long, locale: Locale): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val pattern = bestPattern(locale, "MMMd")
        val formatter = when {
            pattern != null -> DateTimeFormatter.ofPattern(pattern, locale)
            locale.language.equals("pt", ignoreCase = true) ->
                DateTimeFormatter.ofPattern("d 'de' MMM", locale)
            locale.language.equals("en", ignoreCase = true) ->
                DateTimeFormatter.ofPattern("MMM d", locale)
            else -> DateTimeFormatter.ofPattern("d MMM", locale)
        }
        return date.format(formatter)
    }

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

    private fun bestPattern(locale: Locale, skeleton: String): String? =
        runCatching { DateFormat.getBestDateTimePattern(locale, skeleton) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
}
