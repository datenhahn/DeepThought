package net.dankito.synchronization.device.messaging.tcp

import net.dankito.jpa.entitymanager.IEntityManager
import net.dankito.synchronization.device.messaging.IMessenger
import net.dankito.synchronization.device.messaging.callback.IDeviceRegistrationHandler
import net.dankito.synchronization.device.messaging.message.DeviceInfo
import net.dankito.synchronization.device.messaging.message.Response
import net.dankito.synchronization.model.*
import net.dankito.synchronization.model.enums.OsType
import net.dankito.util.ThreadPool
import net.dankito.util.Version
import net.dankito.util.hashing.HashService
import net.dankito.util.hashing.IBase64Service
import net.dankito.util.serialization.JacksonJsonSerializer
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


class PlainTcpMessengerTest {

    companion object {

        protected val MESSAGES_RECEIVER_PORT = 54321

        protected val DEVICE_ID = "1"

        protected val DEVICE_UNIQUE_ID = "Remote_1"

        protected val DEVICE_NAME = "Love"

        protected val DEVICE_OS_NAME = "Arch Linux"

        protected val DEVICE_OS_VERSION = "4.9"

        protected val DEVICE_OS_TYPE = OsType.DESKTOP

        const val IntegrationTestDevicesDiscoveryPrefix = "DeepThought_TcpSocketClientCommunicator_IntegrationTest"

        val AppVersion = Version(1, 0, 0)

        const val DataModelVersion = 1
    }


    private lateinit var underTest: IMessenger


    private lateinit var remoteDevice: Device

    private lateinit var discoveredRemoteDevice: DiscoveredDevice

    private lateinit var destinationAddress: SocketAddress


    @Before
    @Throws(Exception::class)
    fun setUp() {
        setUpRemoteDevice()

        val networkSettings = NetworkSettings(remoteDevice, User("Local", UUID.randomUUID().toString()), IntegrationTestDevicesDiscoveryPrefix, AppVersion, DataModelVersion)

        underTest = PlainTcpMessenger(networkSettings, Mockito.mock(IDeviceRegistrationHandler::class.java), Mockito.mock(IEntityManager::class.java),
                JacksonJsonSerializer(), Mockito.mock(IBase64Service::class.java), HashService(), ThreadPool())

        val countDownLatch = CountDownLatch(1)

        underTest.start(MESSAGES_RECEIVER_PORT) { _, messagesReceiverPort, _ ->
            discoveredRemoteDevice.messagesPort = messagesReceiverPort
            destinationAddress = InetSocketAddress("localhost", messagesReceiverPort)
            countDownLatch.countDown()
        }

        try {
            countDownLatch.await(1, TimeUnit.SECONDS)
        } catch (ignored: Exception) {
        }

    }

    protected fun setUpRemoteDevice() {
        remoteDevice = Device(DEVICE_NAME, DEVICE_UNIQUE_ID, DEVICE_OS_TYPE, DEVICE_OS_NAME, DEVICE_OS_VERSION, "")
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(remoteDevice, DEVICE_ID)

        discoveredRemoteDevice = DiscoveredDevice(remoteDevice, "localhost")
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {

    }

    @Test
    @Throws(Exception::class)
    fun getDeviceInfo() {
        val responseHolder = AtomicReference<Response<DeviceInfo>>()
        val countDownLatch = CountDownLatch(1)

        underTest.getDeviceInfo(destinationAddress) { response ->
            responseHolder.set(response)
            countDownLatch.countDown()
        }


        try {
            countDownLatch.await(1, TimeUnit.MINUTES)
        } catch (ignored: Exception) {
        }

        assertThat(responseHolder.get(), notNullValue())

        val response = responseHolder.get()
        assertThat(response.isCouldHandleMessage, `is`(true))

        val remoteDeviceInfo = response.body
        assertThat<DeviceInfo>(remoteDeviceInfo, notNullValue())

        remoteDeviceInfo?.let { remoteDeviceInfo ->
            assertThat(remoteDeviceInfo.id, `is`(DEVICE_ID))
            assertThat(remoteDeviceInfo.uniqueDeviceId, `is`(DEVICE_UNIQUE_ID))
        }
    }

}