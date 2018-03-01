package net.dankito.service.data

import net.dankito.synchronization.model.LocalFileInfo
import net.dankito.deepthought.service.data.DataManager
import net.dankito.service.data.event.EntityChangedNotifier
import net.dankito.util.event.EntityChangeType


class LocalFileInfoService(dataManager: DataManager, entityChangedNotifier: EntityChangedNotifier)
    : EntityServiceBase<LocalFileInfo>(LocalFileInfo::class.java, dataManager, entityChangedNotifier) {

    override fun callEntitiesUpdatedListenersForCreatedEntity(entity: LocalFileInfo) {
        callEntitiesUpdatedListenersSynchronously(entity, EntityChangeType.Created) // for LocalFileInfo it's important that it gets indexed immediately
    }
}
