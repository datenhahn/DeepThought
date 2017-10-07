package net.dankito.deepthought.android.androidservice

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.RemoteInput
import net.dankito.deepthought.android.MainActivity
import net.dankito.deepthought.android.R
import net.dankito.deepthought.model.Entry
import net.dankito.deepthought.ui.presenter.util.EntryPersister


class PermanentNotificationService(private val context: Context, private val entryPersister: EntryPersister) {

    companion object {
        const val PermanentNotificationNotificationId = 27388
        const val PermanentNotificationRequestCode = 27388
        const val PermanentNotificationTextInputKey = "deepthought_text_input_key"
    }


    fun showPermanentNotification() {
        showNotification(R.string.permanent_notification_content)
    }

    private fun showNotification(contentResourceId: Int) {
        val builder = NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.app_icon_for_status_bar)
    //                .setContentTitle(context.getString(R.string.permanent_notification_title))
                .setContentText(context.getString(contentResourceId))
                .setOngoing(true)
        //                        .setChannelId(CHANNEL_ID)

        // TODO: use a background service instead of an activity
        val startActivityIntent = Intent(context, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(context, PermanentNotificationRequestCode, startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(pendingIntent)

        builder.addAction(createCreateEntryAction(pendingIntent))

        val notification = builder.build()
        notification.flags = Notification.FLAG_NO_CLEAR

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(PermanentNotificationNotificationId, notification)
    }

    private fun createCreateEntryAction(pendingIntent: PendingIntent?): NotificationCompat.Action? {
        val replyLabel = context.getString(R.string.permanent_notification_create_entry_label)
        val remoteInput = RemoteInput.Builder(PermanentNotificationTextInputKey).setLabel(replyLabel).build()

        val action = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_add,
                context.getString(R.string.permanent_notification_create_entry_action), pendingIntent)
                .addRemoteInput(remoteInput)
                .build()
        return action
    }


    fun handlesIntent(intent: Intent): Boolean {
        RemoteInput.getResultsFromIntent(intent)?.let { remoteInput ->
            val input = remoteInput.getCharSequence(PermanentNotificationTextInputKey)

            entryPersister.saveEntryAsync(Entry(input.toString())) { successful ->
                showResultToUser(successful)
            }

            return true
        }

        return false
    }

    private fun showResultToUser(successful: Boolean) {
        if (successful) {
            showNotification(R.string.permanent_notification_reply_successfully_saved_entry)
        }
        else {
            showNotification(R.string.permanent_notification_reply_could_not_save_entry)
        }
    }

}