package app.ecorota.tracking

import android.content.Context
import android.location.Location

/** Community flavor: no Google Play services dependency for location. */
fun createLocationSource(
    context: Context,
    onFix: (Location) -> Unit,
): LocationSource = PlatformLocationSource(context, onFix)
