package net.dankito.service.synchronization.initialsync.model

import net.dankito.deepthought.model.DeepThought
import net.dankito.deepthought.model.enums.ExtensibleEnumeration


data class DeepThoughtSyncInfo(val id: String, val localDeviceId: String, val noteTypeIds: Map<String, String>, val fileTypeIds: Map<String, String>) {

    internal constructor() : this("", "", mapOf(), mapOf()) // for Jackson

    constructor(deepThought: DeepThought) : this(deepThought.id!!, deepThought.localDevice.id!!,
            getMapForExtensibleEnumerationList(deepThought.noteTypes), getMapForExtensibleEnumerationList(deepThought.fileTypes))


    companion object {

        private fun getMapForExtensibleEnumerationList(enumerationSet: Set<ExtensibleEnumeration>): Map<String, String> {
            return enumerationSet.filter { it.nameResourceKey != null }.associateBy( { it.nameResourceKey!! }, { it.id!! } )
        }

    }
}