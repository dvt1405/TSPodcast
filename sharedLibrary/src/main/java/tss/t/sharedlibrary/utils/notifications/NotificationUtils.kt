package tss.t.sharedlibrary.utils.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.MainThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object NotificationUtils {
    const val DEFAULT_FCM_CHANNEL = "Fcm_notification_channel"

    val notificationExecutor by lazy {
        CoroutineScope(Dispatchers.IO)
    }

    fun createChannelIfNeeded(
        context: Context,
        channelId: String = DEFAULT_FCM_CHANNEL
    ) {
        val managerCompat = NotificationManagerCompat.from(context)
        val builder = NotificationChannelCompat.Builder(
            channelId,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
        builder.setShowBadge(true)
        builder.setVibrationEnabled(false)
        builder.setSound(null, null)
        builder.setDescription("TSS group notifications")
        val isExists = managerCompat.getNotificationChannel(channelId) != null
        if (!isExists) {
            managerCompat.createNotificationChannel(builder.build())
        }
    }

    @MainThread
    fun pushNotification(
        context: Context,
        channelId: String = DEFAULT_FCM_CHANNEL,
        title: String?,
        message: String?,
        bigImage: String?
    ) {
        notificationExecutor.launch {
            val bitmap = ImageLoader(context)
                .execute(
                    ImageRequest.Builder(context)
                        .data(bigImage)
                        .build()
                ).drawable?.toBitmapOrNull()

            withContext(Dispatchers.Main) {
                createChannelIfNeeded(context = context, channelId)
                val notificationCompat = NotificationCompat.Builder(context, channelId)
                notificationCompat.setContentTitle(title)
                notificationCompat.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .setBigContentTitle(message)
                        .setSummaryText(message)
                        .bigPicture(bitmap)
                        .let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                it.showBigPictureWhenCollapsed(true)
                            }
                            it
                        }
                )
                val notification = notificationCompat.build()
                if (ActivityCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext
                }

                NotificationManagerCompat.from(context)
                    .notify(
                        12345,
                        notification
                    )
            }
        }
    }
}