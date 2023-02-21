package eu.kanade.tachiyomi.util.chapter.exh.util

import android.net.Uri

/**
 * Uri filter
 */
interface UriFilter {
    fun addToUri(builder: Uri.Builder)
}
