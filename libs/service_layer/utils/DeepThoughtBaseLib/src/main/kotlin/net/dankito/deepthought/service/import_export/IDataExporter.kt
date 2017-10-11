package net.dankito.deepthought.service.import_export

import net.dankito.deepthought.model.Entry
import java.io.File


interface IDataExporter {

    val name: String
        get


    fun exportAsync(destinationFile: File, entries: Collection<Entry>)

    fun export(destinationFile: File, entries: Collection<Entry>)

}