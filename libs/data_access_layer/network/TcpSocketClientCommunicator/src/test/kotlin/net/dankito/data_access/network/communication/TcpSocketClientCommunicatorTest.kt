package net.dankito.data_access.network.communication

import net.dankito.data_access.network.communication.callback.ClientCommunicatorListener
import net.dankito.data_access.network.communication.callback.IsSynchronizationPermittedHandler
import net.dankito.data_access.network.communication.callback.SendRequestCallback
import net.dankito.data_access.network.communication.message.DeviceInfo
import net.dankito.data_access.network.communication.message.Response
import net.dankito.deepthought.model.Device
import net.dankito.deepthought.model.DiscoveredDevice
import net.dankito.deepthought.model.NetworkSettings
import net.dankito.deepthought.model.OsType
import net.dankito.utils.ThreadPool
import net.dankito.utils.services.hashing.IBase64Service
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


class TcpSocketClientCommunicatorTest {


    private lateinit var underTest: IClientCommunicator


    private lateinit var remoteDevice: Device

    private lateinit var discoveredRemoteDevice: DiscoveredDevice

    private lateinit var destinationAddress: SocketAddress


    @Before
    @Throws(Exception::class)
    fun setUp() {
        setUpRemoteDevice()

        val networkSettings = NetworkSettings(remoteDevice)

        underTest = TcpSocketClientCommunicator(networkSettings, Mockito.mock(IsSynchronizationPermittedHandler::class.java), Mockito.mock(IBase64Service::class.java), ThreadPool())

        val countDownLatch = CountDownLatch(1)

        underTest.start(MESSAGES_RECEIVER_PORT, object : ClientCommunicatorListener {
            override fun started(couldStartMessagesReceiver: Boolean, messagesReceiverPort: Int, startException: Exception?) {
                discoveredRemoteDevice.messagesPort = messagesReceiverPort
                destinationAddress = InetSocketAddress("localhost", messagesReceiverPort)
                countDownLatch.countDown()
            }
        })

        try {
            countDownLatch.await(1, TimeUnit.SECONDS)
        } catch (ignored: Exception) {
        }

    }

    protected fun setUpRemoteDevice() {
        remoteDevice = Device(DEVICE_ID, DEVICE_NAME, DEVICE_UNIQUE_ID, DEVICE_OS_TYPE, DEVICE_OS_NAME, DEVICE_OS_VERSION, "")

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

        underTest.getDeviceInfo(destinationAddress, object : SendRequestCallback<DeviceInfo> {
            override fun done(response: Response<DeviceInfo>) {
                responseHolder.set(response)
                countDownLatch.countDown()
            }
        })


        try {
            countDownLatch.await(1, TimeUnit.MINUTES)
        } catch (ignored: Exception) {
        }

        assertThat(responseHolder.get(), notNullValue())

        val response = responseHolder.get()
        assertThat(response.isCouldHandleMessage, `is`(true))

        val remoteDeviceInfo = response.body
        assertThat<DeviceInfo>(remoteDeviceInfo, notNullValue())
        assertThat(remoteDeviceInfo!!.id, `is`(DEVICE_ID))
        assertThat(remoteDeviceInfo.uniqueDeviceId, `is`(DEVICE_UNIQUE_ID))
        assertThat(remoteDeviceInfo.name, `is`(DEVICE_NAME))
        assertThat(remoteDeviceInfo.osName, `is`(DEVICE_OS_NAME))
        assertThat(remoteDeviceInfo.osVersion, `is`(DEVICE_OS_VERSION))
        assertThat(remoteDeviceInfo.osType, `is`(DEVICE_OS_TYPE))
    }

    companion object {

        protected val MESSAGES_RECEIVER_PORT = 54321

        protected val DEVICE_ID = "1"

        protected val DEVICE_UNIQUE_ID = "Remote_1"

        protected val DEVICE_NAME = "Love"

        protected val DEVICE_OS_NAME = "Arch Linux"

        protected val DEVICE_OS_VERSION = "4.9"

        protected val DEVICE_OS_TYPE = OsType.DESKTOP
    }

}