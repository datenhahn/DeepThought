package net.dankito.synchronization.model


open class SyncInfo(val localDeviceId: String, val user: UserSyncInfo, val useCallerDatabaseIds: Boolean? = null, val useCallerUserName: Boolean? = null) {

    constructor() : this("", UserSyncInfo(), false, false) // for Jackson
}