package net.dankito.service.data.messages

import net.dankito.synchronization.model.LocalFileInfo


class LocalFileInfoChanged(entity: LocalFileInfo, changeType: EntityChangeType, source: EntityChangeSource): EntityChanged<LocalFileInfo>(entity, changeType, source)