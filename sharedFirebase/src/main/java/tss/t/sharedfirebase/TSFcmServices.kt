package tss.t.sharedfirebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import tss.t.sharedlibrary.utils.notifications.NotificationConstants
import tss.t.sharedlibrary.utils.notifications.NotificationUtils
import javax.inject.Inject

@AndroidEntryPoint
class TSFcmServices() : FirebaseMessagingService() {

    @Inject
    lateinit var tsAnalytics: TSAnalytics

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createChannelIfNeeded(this)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    override fun onMessageSent(msgId: String) {
        super.onMessageSent(msgId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
        val title = message.data["title"]
        val content = message.data["content"]
        val bigImage = message.data["big_image"]

        when (type) {
            NotificationConstants.TYPE_BANNER_ADS,
            NotificationConstants.TYPE_VIDEO_ADS -> {
                handleInAppAds(
                    type = type,
                    title = title,
                    content = content,
                    bigImage = bigImage,
                    data = message.data
                )
            }

            NotificationConstants.TYPE_FULL_SCREEN_ADS -> {
                startAdsIfNeeded(
                    type = type,
                    title = title,
                    content = content,
                    bigImage = bigImage,
                    data = message.data
                )
            }

            NotificationConstants.TYPE_NEW_PODCAST -> {
                notifyNewPodcast(
                    type = type,
                    title = title,
                    content = content,
                    bigImage = bigImage,
                    data = message.data
                )
            }

            else -> super.onMessageReceived(message)
        }
        listeners.onEach {
            it.onMessageReceived(
                type = type,
                title = title,
                content = content,
                bigImage = bigImage, data = message.data
            )
        }

        tsAnalytics.trackEvent(
            "fcm_${type}_message_receive",
            tsAnalytics.currentScreenName,
            *message.data.toList().toTypedArray()
        )
    }

    private fun notifyNewPodcast(
        type: String,
        title: String?,
        content: String?,
        bigImage: String?,
        data: Map<String, String>
    ) {
        NotificationUtils.pushNotification(
            this,
            title = title,
            message = content,
            bigImage = bigImage
        )

    }

    private fun startAdsIfNeeded(
        type: String,
        title: String?,
        content: String?,
        bigImage: String?,
        data: Map<String, String>
    ) {
        NotificationUtils.pushNotification(
            this,
            title = title,
            message = content,
            bigImage = bigImage
        )
    }

    private fun handleInAppAds(
        type: String,
        title: String?,
        content: String?,
        bigImage: String?,
        data: Map<String, String>
    ) {
        NotificationUtils.pushNotification(
            this,
            title = title,
            message = content,
            bigImage = bigImage
        )
    }

    fun interface OnMessageReceive {
        fun onMessageReceived(
            type: String?,
            title: String?,
            content: String?,
            bigImage: String?,
            data: Map<String, String>
        )
    }

    companion object {
        val listeners by lazy {
            mutableListOf<OnMessageReceive>()
        }

        fun addListener(onMessageReceive: OnMessageReceive) {
            synchronized(listeners) {
                listeners.add(onMessageReceive)
            }
        }

        fun removeListener(onMessageReceive: OnMessageReceive) {
            synchronized(listeners) {
                if (listeners.contains(onMessageReceive)) {
                    listeners.remove(onMessageReceive)
                }
            }
        }
    }
}