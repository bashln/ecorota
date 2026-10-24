package app.ecorota.tracking

import android.content.Context
import android.location.Location

/** Play flavor: Fused Location Provider, with a platform fallback. */
fun createLocationSource(
    context: Context,
    onFix: (Location) -> Unit,
): LocationSource = FusedLocationSource(context, onFix)
