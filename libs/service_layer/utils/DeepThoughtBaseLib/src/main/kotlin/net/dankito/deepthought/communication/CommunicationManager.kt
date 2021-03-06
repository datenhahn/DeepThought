package net.dankito.deepthought.communication

import net.dankito.data_access.network.communication.CommunicatorConfig
import net.dankito.data_access.network.communication.IClientCommunicator
import net.dankito.deepthought.model.INetworkSettings
import net.dankito.service.synchronization.IConnectedDevicesService
import net.dankito.service.synchronization.ISyncManager
import java.util.*


class CommunicationManager(private val connectedDevicesService: IConnectedDevicesService, private val syncManager: ISyncManager, private val clientCommunicator: IClientCommunicator,
                           private val networkSettings: INetworkSettings) : ICommunicationManager {

    override fun startAsync() {
        clientCommunicator.start(CommunicatorConfig.DEFAULT_MESSAGES_RECEIVER_PORT) { couldStartMessagesReceiver, messagesReceiverPort, startException ->
            if (couldStartMessagesReceiver) {
                successfullyStartedClientCommunicator(messagesReceiverPort)
            }
            else {
                startException?.let {
                    startingClientCommunicatorFailed(startException)
                }
            }
        }
    }

    override fun stop() {
        connectedDevicesService.stop()

        clientCommunicator.stop()

        syncManager.stop()
    }


    private fun startingClientCommunicatorFailed(startException: Exception) {
        // TODO: what to do in error case?
    }

    private fun successfullyStartedClientCommunicator(messagesReceiverPort: Int) {
        networkSettings.messagePort = messagesReceiverPort

        val random = Random(System.nanoTime())

        val desiredSynchronizationPort = messagesReceiverPort + random.nextInt(400) // TODO: desiredSynchronizationPort is never used
        val desiredBasicDataSynchronizationPort = desiredSynchronizationPort + random.nextInt(700) // TODO: using a random port is really ugly

        syncManager.startAsync(desiredSynchronizationPort, desiredBasicDataSynchronizationPort) {
            successfullyStartedSyncManager(it)
        }
    }

    private fun successfullyStartedSyncManager(basicDataSyncPort: Int) {
        connectedDevicesService.start()
    }

}
