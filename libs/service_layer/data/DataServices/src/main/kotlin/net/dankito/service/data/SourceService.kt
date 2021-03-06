package net.dankito.service.data

import net.dankito.deepthought.model.Source
import net.dankito.deepthought.service.data.DataManager
import net.dankito.service.data.event.EntityChangedNotifier


class SourceService(dataManager: DataManager, entityChangedNotifier: EntityChangedNotifier) : EntityServiceBase<Source>(Source::class.java, dataManager, entityChangedNotifier)