package net.dankito.deepthought.javafx.dialogs.mainwindow.model

import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleStringProperty
import net.dankito.deepthought.extensions.previewWithSeriesAndPublishingDate
import net.dankito.deepthought.model.Entry
import tornadofx.*


class EntryViewModel : ItemViewModel<Entry>() {

    val index = bind { SimpleLongProperty(item?.entryIndex ?: 0) }

    val reference = bind { SimpleStringProperty(item?.reference.previewWithSeriesAndPublishingDate ?: "") }

    val preview = bind { SimpleStringProperty(item?.preview ?: "") }

    val createdOn = bind { SimpleLongProperty(item?.createdOn?.time ?: 0) }

    val modifiedOn = bind { SimpleLongProperty(item?.modifiedOn?.time ?: 0) }

}