package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source

interface RandomMangaSource : Source {
    suspend fun fetchRandomMangaUrl(): String
}
