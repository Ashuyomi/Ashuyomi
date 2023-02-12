package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.SourceManager
import uy.kohesive.injekt.injectLazy

class MigrationSourceAdapter(
    listener: OnItemClickListener,
) : FlexibleAdapter<MigrationSourceItem>(
    null,
    listener,
    true,
) {
    val sourceManager: SourceManager by injectLazy()

    // SY _->
    val sourcePreferences: SourcePreferences by injectLazy()
    // SY <--
}
