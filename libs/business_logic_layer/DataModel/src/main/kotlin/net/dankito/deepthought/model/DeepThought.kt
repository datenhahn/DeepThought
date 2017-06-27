package net.dankito.deepthought.model

import net.dankito.deepthought.model.config.TableConfig
import net.dankito.deepthought.model.enums.ApplicationLanguage
import net.dankito.deepthought.model.enums.FileType
import net.dankito.deepthought.model.enums.NoteType
import java.io.Serializable
import java.util.*
import javax.persistence.*


@Entity(name = TableConfig.DeepThoughtTableName)
data class DeepThought(

        @OneToOne(fetch = FetchType.EAGER, cascade = arrayOf(CascadeType.PERSIST))
        @JoinColumn(name = TableConfig.DeepThoughtLocalUserJoinColumnName)
        var localUser: User,

        @OneToOne(fetch = FetchType.EAGER, cascade = arrayOf(CascadeType.PERSIST))
        @JoinColumn(name = TableConfig.DeepThoughtLocalDeviceJoinColumnName)
        val localDevice: Device

) : BaseEntity(), Serializable {


    companion object {
        private const val serialVersionUID = -3232937271770851228L
    }


    @Column(name = TableConfig.DeepThoughtDataModelVersionColumnName)
    val dataModelVersion = 1


    @OneToMany(fetch = FetchType.LAZY, cascade = arrayOf(CascadeType.PERSIST))
    var devices: MutableSet<Device> = HashSet()
        private set


    @OneToMany(fetch = FetchType.EAGER, cascade = arrayOf(CascadeType.PERSIST))
    @OrderBy(value = "sortOrder")
    var applicationLanguages: MutableSet<ApplicationLanguage> = TreeSet<ApplicationLanguage>() // these are the Languages the UI can display
        private set

    @OneToMany(fetch = FetchType.LAZY, cascade = arrayOf(CascadeType.PERSIST))
    @OrderBy(value = "sortOrder")
    var noteTypes: MutableSet<NoteType> = TreeSet<NoteType>()
        private set

    @OneToMany(fetch = FetchType.LAZY, cascade = arrayOf(CascadeType.PERSIST))
    @OrderBy(value = "sortOrder")
    var fileTypes: MutableSet<FileType> = TreeSet<FileType>()
        private set


    @Column(name = TableConfig.DeepThoughtNextEntryIndexColumnName)
    var nextEntryIndex = 0L
        private set


    private constructor() : this(User(), Device())


    fun addDevice(device: Device): Boolean {
        if (devices.add(device)) {
            return true
        }

        return false
    }

    fun removeDevice(device: Device): Boolean {
        if (localDevice == device) { // don't delete local device!
            return false
        }

        if (devices.remove(device)) {
            return true
        }

        return false
    }


    fun addApplicationLanguage(applicationLanguage: ApplicationLanguage): Boolean {
        return applicationLanguages.add(applicationLanguage)
    }

    fun removeApplicationLanguage(applicationLanguage: ApplicationLanguage): Boolean {
        if (applicationLanguage.isSystemValue) {
            return false
        }

        return applicationLanguages.remove(applicationLanguage)
    }


    fun addNoteType(noteType: NoteType): Boolean {
        return noteTypes.add(noteType)
    }

    fun removeNoteType(noteType: NoteType): Boolean {
        if (noteType.isSystemValue) {
            return false
        }

        return noteTypes.remove(noteType)
    }

    fun addFileType(fileType: FileType): Boolean {
        return fileTypes.add(fileType)
    }

    fun removeFileType(fileType: FileType): Boolean {
        if (fileType.isSystemValue) {
            return false
        }

        return fileTypes.remove(fileType)
    }


    fun increaseNextEntryIndex(): Long {
        synchronized(this) {
            return ++nextEntryIndex
        }
    }

}
