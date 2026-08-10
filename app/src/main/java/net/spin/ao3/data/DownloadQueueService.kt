package net.spin.ao3.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.spin.ao3.Ao3App
import net.spin.ao3.MainActivity
import net.spin.ao3.R
import net.spin.ao3.data.model.ChapterInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sequential download queue with a foreground progress notification.
 *
 * WorkDetailScreen enqueues a whole work ("Descargar todo") with
 * [enqueueIntent]; the service fetches every chapter politely (Ao3Client
 * serializes requests with delays), stores it and updates the notification.
 * Live queue state is exposed to the UI through [state].
 */
class DownloadQueueService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store get() = (application as Ao3App).container.store
    private val client get() = (application as Ao3App).container.client

    private val pending = ArrayDeque<Job>()
    private var processing = false
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val job = intent.toJob()
                if (job != null) {
                    // Persist first so the queue survives process death.
                    store.addPendingJob(job.toPending())
                    synchronized(pending) { pending += job }
                    ensureForeground(job)
                    processPending()
                }
            }
            ACTION_RESUME -> {
                // Restore the persisted queue (e.g. after the app was killed
                // mid-download) and keep downloading.
                if (pending.isEmpty()) {
                    val restored = store.pendingJobs().map { it.toJob() }
                    synchronized(pending) { pending.addAll(restored) }
                }
                val first = synchronized(pending) { pending.firstOrNull() }
                if (first != null) {
                    ensureForeground(first)
                    processPending()
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureForeground(job: Job) {
        if (foregroundStarted) return
        startForegroundCompat(
            baseNotification(job)
                .setContentTitle("Descargas")
                .setContentText("Preparando descarga…")
                .setOngoing(true)
                .build(),
        )
        foregroundStarted = true
    }

    override fun onDestroy() {
        scope.cancel()
        // If the system kills us mid-queue (force-stop, OEM battery management),
        // reset the live state so the UI doesn't stay stuck on "active".
        if (processing) {
            state.value = QueueState()
        }
        super.onDestroy()
    }

    private fun processPending() {
        if (processing) return
        processing = true
        scope.launch {
            try {
                while (true) {
                    val job = synchronized(pending) { pending.removeFirstOrNull() } ?: break
                    runJob(job)
                }
            } finally {
                processing = false
                // The queue is drained: keep the "completed" notification and
                // release the foreground service so it doesn't linger forever.
                if (Build.VERSION.SDK_INT >= 24) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private suspend fun runJob(job: Job) {
        val total = job.chapters.size
        var okCount = 0
        state.value = QueueState(active = true, workId = job.workId, workTitle = job.title, done = 0, total = total)
        job.chapters.forEachIndexed { i, chapter ->
            try {
                val ready = if (chapter.content != null) chapter else client.getChapter(job.workId, chapter)
                store.addDownloadedChapter(job.workId, job.title, ready)
                okCount++
            } catch (_: Exception) {
                // Keep going with the next chapter; partial downloads are fine.
            }
            val done = i + 1
            state.value = state.value.copy(done = done, current = chapter.title)
            startForegroundCompat(
                baseNotification(job)
                    .setContentTitle("Descargando: ${job.title}")
                    .setContentText("Capítulo $done de $total")
                    .setProgress(total, done, false)
                    .setOngoing(true)
                    .build(),
            )
        }
        store.removePendingJob(job.workId)
        state.value = QueueState(active = false, workTitle = job.title, total = total, done = total, completedAt = System.currentTimeMillis())
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTIFICATION_ID,
            baseNotification(job)
                .setContentTitle("Descarga completada")
                .setContentText(
                    if (okCount == total) {
                        "$total ${if (total == 1) "capítulo" else "capítulos"} guardados · ${job.title}"
                    } else {
                        "$okCount de $total capítulos guardados · ${job.title}"
                    },
                )
                .setProgress(0, 0, false)
                .build(),
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Descargas", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progreso de descargas de obras"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun baseNotification(job: Job): NotificationCompat.Builder {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun Intent.toJob(): Job? {
        val workId = getLongExtra("workId", -1L)
        val title = getStringExtra("title") ?: return null
        val raw = getStringExtra("chapters") ?: return null
        val chapters = runCatching { JSONArray(raw).toChapters() }.getOrElse { emptyList() }
        if (workId < 0 || chapters.isEmpty()) return null
        return Job(workId, title, chapters)
    }

    private fun Job.toPending(): Store.PendingJob =
        Store.PendingJob(workId, title, chapters)

    private fun Store.PendingJob.toJob(): Job =
        Job(workId, title, chapters)

    companion object {
        const val ACTION_DOWNLOAD = "net.spin.ao3.action.DOWNLOAD"
        const val ACTION_RESUME = "net.spin.ao3.action.RESUME"
        private const val CHANNEL_ID = "descargas"
        const val NOTIFICATION_ID = 42

        /** Live queue state observed by the UI. */
        data class QueueState(
            val active: Boolean = false,
            val workId: Long? = null,
            val workTitle: String = "",
            val done: Int = 0,
            val total: Int = 0,
            val current: String = "",
            /** Monotonic timestamp of the last completed job (drives UI events). */
            val completedAt: Long = 0L,
        )

        val state = MutableStateFlow(QueueState())

        data class Job(
            val workId: Long,
            val title: String,
            val chapters: List<ChapterInfo>,
        )

        /**
         * Starts the service if there are persisted jobs waiting (used at app
         * launch so an interrupted download resumes automatically).
         */
        fun startIfPending(context: Context) {
            val app = context.applicationContext as Ao3App
            if (app.container.store.pendingJobs().isNotEmpty()) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadQueueService::class.java).apply {
                        action = ACTION_RESUME
                    },
                )
            }
        }

        fun enqueueIntent(
            context: Context,
            workId: Long,
            title: String,
            chapters: List<ChapterInfo>,
        ): Intent = Intent(context, DownloadQueueService::class.java).apply {
            action = ACTION_DOWNLOAD
            putExtra("workId", workId)
            putExtra("title", title)
            putExtra("chapters", chapters.toJsonArray().toString())
        }

        private fun List<ChapterInfo>.toJsonArray(): JSONArray = JSONArray().apply {
            forEach { ch ->
                put(JSONObject().apply {
                    put("index", ch.index)
                    put("title", ch.title)
                    put("url", ch.url ?: "")
                    ch.chapterId?.let { put("chapterId", it) }
                    ch.content?.let { put("content", it) }
                })
            }
        }

        private fun JSONArray.toChapters(): List<ChapterInfo> = (0 until length()).mapNotNull { i ->
            val o = optJSONObject(i) ?: return@mapNotNull null
            ChapterInfo(
                index = o.optInt("index", 0),
                title = o.optString("title", ""),
                url = o.optString("url", "").ifEmpty { null },
                chapterId = if (o.has("chapterId")) o.optLong("chapterId") else null,
                content = o.optString("content", "").ifEmpty { null },
            )
        }
    }
}
