package id.miru.app.ui.util

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

/**
 * Optimized Coil ImageRequest factory for buttery-smooth 60/120Hz scrolling.
 * Employs hardware-friendly INEXACT downsampling and eliminates crossfade stalls.
 */
object ImageRequests {
    fun poster(context: Context, url: String?): ImageRequest {
        return ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .size(360, 540)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    fun thumbnail(context: Context, url: String?): ImageRequest {
        return ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .size(480, 270)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    fun heroBanner(context: Context, url: String?): ImageRequest {
        return ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .size(1080, 720)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
