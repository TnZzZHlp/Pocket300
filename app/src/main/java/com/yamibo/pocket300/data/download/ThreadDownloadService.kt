package com.yamibo.pocket300.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yamibo.pocket300.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground owner of actual thread-download execution.
 *
 * The service contains no queue policy: it starts the application [ThreadDownloadManager] worker
 * after Android has accepted the foreground-service request, publishes its observable progress,
 * and stops when the durable queue becomes idle.
 */
class ThreadDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val downloadManager by lazy(LazyThreadSafetyMode.NONE) {
        ThreadDownloadManager.getInstance(applicationContext)
    }

    private var stateJob: Job? = null
    private var idleStopJob: Job? = null
    private var latestStartId = 0
    private var serviceGeneration: Long? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        idleStopJob?.cancel()
        showForegroundNotification(buildNotification())
        serviceScope.launch {
            serviceGeneration = downloadManager.downloaderStart()
            observeQueue()
            updateIdleStop(downloadManager.queueState.value)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        idleStopJob?.cancel()
        stateJob?.cancel()
        serviceGeneration?.let(downloadManager::downloaderStop)
        serviceScope.cancel()
        super.onDestroy()
    }

    @Suppress("NewApi")
    override fun onTimeout(startId: Int, fgsType: Int) {
        serviceGeneration?.let(downloadManager::pauseForServiceTimeout)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeQueue() {
        if (stateJob?.isActive == true) return
        stateJob = serviceScope.launch {
            combine(downloadManager.statuses, downloadManager.queueState) { statuses, queueState ->
                statuses to queueState
            }.collect { (statuses, queueState) ->
                showForegroundNotification(buildNotification(statuses, queueState))
                updateIdleStop(queueState)
            }
        }
    }

    private fun updateIdleStop(queueState: ThreadDownloadQueueState) {
        if (!queueState.isPaused && queueState.orderedKeys.isNotEmpty()) {
            idleStopJob?.cancel()
            idleStopJob = null
            return
        }
        if (idleStopJob?.isActive == true) return
        val observedStartId = latestStartId
        val observedGeneration = serviceGeneration ?: return
        idleStopJob = serviceScope.launch {
            delay(IDLE_STOP_DELAY_MILLIS)
            val latestQueueState = downloadManager.queueState.value
            if (
                serviceGeneration == observedGeneration &&
                (latestQueueState.isPaused || latestQueueState.orderedKeys.isEmpty())
            ) {
                stopSelfResult(observedStartId)
            }
        }
    }

    private fun showForegroundNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        statuses: Map<ThreadDownloadKey, ThreadDownloadStatus> = emptyMap(),
        queueState: ThreadDownloadQueueState = ThreadDownloadQueueState(),
    ): Notification {
        val activeStatus = queueState.activeKey?.let(statuses::get)
        val text = when (activeStatus?.phase) {
            ThreadDownloadPhase.FETCHING_PAGES -> getString(
                R.string.download_service_notification_fetching,
                activeStatus.thread.subject,
            )

            ThreadDownloadPhase.DOWNLOADING_IMAGES -> getString(
                R.string.download_service_notification_images,
                activeStatus.thread.subject,
                activeStatus.progress.completedImages,
                activeStatus.progress.totalImages,
            )

            else -> getString(
                R.string.download_service_notification_waiting,
                queueState.orderedKeys.size,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.download_service_notification_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.download_service_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    companion object {
        private const val CHANNEL_ID = "thread_downloads"
        private const val NOTIFICATION_ID = 300
        private const val IDLE_STOP_DELAY_MILLIS = 250L

        fun intent(context: Context): Intent = Intent(context, ThreadDownloadService::class.java)
    }
}
