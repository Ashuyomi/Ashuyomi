package eu.kanade.data.updates

import eu.kanade.data.AndroidDatabaseHandler
import eu.kanade.data.DatabaseHandler
import eu.kanade.domain.updates.model.UpdatesWithRelations
import eu.kanade.domain.updates.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UpdatesRepositoryImpl(
    val databaseHandler: DatabaseHandler,
) : UpdatesRepository {

    override fun subscribeAll(after: Long): Flow<List<UpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            updatesViewQueries.updates(after, updateWithRelationMapper)
        }.map {
            databaseHandler.awaitList { (databaseHandler as AndroidDatabaseHandler).getUpdatesQuery(after) }
        }
    }
}
