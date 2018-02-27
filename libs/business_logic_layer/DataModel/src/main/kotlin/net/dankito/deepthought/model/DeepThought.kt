package net.dankito.deepthought.model

import net.dankito.deepthought.model.config.TableConfig
import net.dankito.synchronization.model.BaseEntity
import net.dankito.synchronization.model.Device
import net.dankito.synchronization.model.User
import net.dankito.synchronization.model.enums.OsType
import java.io.Serializable
import javax.persistence.*


@Entity(name = TableConfig.DeepThoughtTableName)
data class DeepThought(

        @OneToOne(fetch = FetchType.EAGER, cascade = arrayOf(CascadeType.PERSIST))
        @JoinColumn(name = TableConfig.DeepThoughtLocalUserJoinColumnName)
        var localUser: User,

        @OneToOne(fetch = FetchType.EAGER, cascade = arrayOf(CascadeType.PERSIST))
        @JoinColumn(name = TableConfig.DeepThoughtLocalDeviceJoinColumnName)
        val localDevice: Device,

        @OneToOne(fetch = FetchType.EAGER, cascade = arrayOf(CascadeType.PERSIST))
        @JoinColumn(name = TableConfig.DeepThoughtLocalSettingsJoinColumnName)
        var localSettings: LocalSettings

) : BaseEntity(), Serializable {


    companion object {
        private const val serialVersionUID = -3232937271770851228L
    }


    @Column(name = TableConfig.DeepThoughtNextItemIndexColumnName)
    var nextItemIndex = 0L
        private set


    private constructor() : this(User("", ""), Device("", "", OsType.DESKTOP), LocalSettings())


    fun increaseNextItemIndex(): Long {
        synchronized(this) {
            return ++nextItemIndex
        }
    }

}
