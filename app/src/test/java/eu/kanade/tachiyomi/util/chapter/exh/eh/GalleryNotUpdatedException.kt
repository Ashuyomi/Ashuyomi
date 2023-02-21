package eu.kanade.tachiyomi.util.chapter.exh.eh

class GalleryNotUpdatedException(val network: Boolean, cause: Throwable) : RuntimeException(cause)
