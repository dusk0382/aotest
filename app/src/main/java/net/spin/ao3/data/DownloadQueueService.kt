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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.coroutines.coroutineContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Sequential, resumable download queue with a foreground progress notification.
 * Progress advances only after a chapter is actually stored. Network failures
 * pause the queue and leave the remaining chapters pending for an explicit
 * resume, instead of pretending that the request succeeded.
 */
class DownloadQueueService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store get() = (application as Ao3App).container.store
    private val client get() = (application as Ao3App).container.client

    private val pending = ArrayDeque<JobInfo>()
    // Teardown del worker encolado al hilo principal: onStartCommand y el
    // cierre de la cola quedan así ordenados sin carreras (ver processPending).
    private val mainHandler = Handler(Looper.getMainLooper())
    // processing/foregroundStarted are written from the worker coroutine
    // (Dispatchers.IO) and read from main-thread onStartCommand: without
    // @Volatile a stale read can skip startForeground or double-start work.
    @Volatile private var processing = false
    @Volatile private var foregroundStarted = false
    private var worker: Job? = null
    @Volatile private var cancelRequested = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                // Arrancado vía startForegroundService: hay que llamar
                // startForeground SIEMPRE, también con un intent inválido
                // (extras corruptos o lista de capítulos vacía), o el sistema
                // mata la app ~5 s después (ForegroundServiceDidNotStartInTime).
                val job = intent.toJob()
                cancelRequested = false
                ensureForeground(job)
                if (job != null) {
                    store.addPendingJob(job.toPending())
                    synchronized(pending) {
                        pending.removeAll { it.workId == job.workId }
                        pending += job
                    }
                    processPending()
                } else if (!processing) {
                    stopSelf()
                }
            }
            ACTION_RESUME -> {
                // Mismo contrato que ACTION_DOWNLOAD: la cola restaurada puede
                // venir vacía (p.ej. tras una descarga cancelada que dejó el
                // estado «pausado» en pantalla y sin trabajo persistido), pero
                // startForeground no es opcional.
                cancelRequested = false
                if (pending.isEmpty()) {
                    val restored = store.pendingJobs().map { it.toJob() }
                    synchronized(pending) { pending.addAll(restored) }
                }
                val first = synchronized(pending) { pending.firstOrNull() }
                ensureForeground(first)
                if (first != null) {
                    processPending()
                } else if (!processing) {
                    stopSelf()
                }
            }
            ACTION_CANCEL -> {
                val workId = intent.getLongExtra(EXTRA_WORK_ID, -1L)
                cancelRequested = true
                if (workId >= 0) {
                    store.removePendingJob(workId)
                    synchronized(pending) { pending.removeAll { it.workId == workId } }
                } else {
                    synchronized(pending) { pending.clear() }
                }
                state.value = state.value.copy(
                    active = false,
                    paused = false,
                    errorMessage = null,
                    cancelledAt = System.currentTimeMillis(),
                )
                worker?.cancel()
                if (!processing) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Acepta [job] nulo: un intent inválido también exige startForeground. */
    private fun ensureForeground(job: JobInfo?) {
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
        worker = null
        scope.cancel()
        if (processing && state.value.active) state.value = QueueState()
        super.onDestroy()
    }

    private fun processPending() {
        if (processing) return
        processing = true
        worker = scope.launch {
            try {
                while (true) {
                    val job = synchronized(pending) { pending.removeFirstOrNull() } ?: break
                    val completed = runJob(job)
                    if (!completed) break
                }
            } finally {
                // El cierre corre en el HILO PRINCIPAL y solo si esta sesión
                // sigue siendo la dueña (worker === self). Antes, el finally en
                // IO podía intercalarse con un ACTION_DOWNLOAD que acababa de
                // arrancar una cola nueva: le pisaba foregroundStarted/processing
                // o llegaba a ejecutar stopSelf() DESTRUYENDO el servicio con el
                // worker nuevo dentro. En el hilo principal el bloque es atómico
                // frente a onStartCommand; si otra sesión es la dueña, no toca
                // nada (esa sesión ya posee el foreground y el estado).
                val self = coroutineContext[Job]
                mainHandler.post {
                    if (worker === self) {
                        worker = null
                        // Clear foregroundStarted BEFORE publishing
                        // processing=false: any ACTION_DOWNLOAD that then
                        // observes processing==false also observes that it must
                        // call startForeground itself (API 26+ demands one per
                        // startForegroundService).
                        foregroundStarted = false
                        processing = false
                        if (Build.VERSION.SDK_INT >= 24) {
                            stopForeground(STOP_FOREGROUND_DETACH)
                        } else {
                            stopForeground(true)
                        }
                        if (!processing) stopSelf()
                    }
                }
            }
        }
    }

    /** Returns true when completed; false when paused after a network failure. */
    private suspend fun runJob(job: JobInfo): Boolean {
        val total = job.chapters.size
        val alreadyDownloaded = store.downloadedChapterIds(job.workId)
        var done = job.chapters.count { it.index in alreadyDownloaded }
        state.value = QueueState(
            active = true,
            workId = job.workId,
            workTitle = job.title,
            done = done,
            total = total,
        )

        job.chapters.forEachIndexed { i, chapter ->
            if (cancelRequested) throw CancellationException("Descarga cancelada")
            if (chapter.index in alreadyDownloaded) {
                publishProgress(job, chapter, done, total)
                return@forEachIndexed
            }
            try {
                val ready = if (chapter.content != null) chapter else client.getChapter(job.workId, chapter)
                if (ready.content.isNullOrBlank()) throw IllegalStateException("El capítulo llegó vacío")
                store.addDownloadedChapter(job.workId, job.title, ready)
                done++
                val remaining = job.chapters.drop(i + 1)
                if (!cancelRequested) {
                    // Persist only the not-yet-attempted part. If Android kills
                    // the service now, the completed chapters are not downloaded
                    // twice.
                    if (remaining.isEmpty()) store.removePendingJob(job.workId)
                    else store.addPendingJob(Store.PendingJob(job.workId, job.title, remaining))
                    // Close the read-flag/persist race with ACTION_CANCEL: if
                    // cancel landed between the check above and the write, undo
                    // it — a cancelled job must never survive in "pending"
                    // (startIfPending would resurrect it on next launch).
                    if (cancelRequested) store.removePendingJob(job.workId)
                }
                // else: ACTION_CANCEL already removed this job while the chapter
                // was saving; re-adding here would resurrect a cancelled queue.
                publishProgress(job, chapter, done, total)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Do not advance done on failure. Keep this chapter in the
                // persisted queue so Resume retries it later — unless the user
                // cancelled while the request was in flight; then the job must
                // stay removed.
                if (!cancelRequested) {
                    store.addPendingJob(Store.PendingJob(job.workId, job.title, job.chapters.drop(i)))
                    // Same race-closer as the success path above: if cancel
                    // landed between the check and the write, undo it — a
                    // cancelled job must never survive in "pending"
                    // (startIfPending would resurrect it on next launch).
                    if (cancelRequested) store.removePendingJob(job.workId)
                }
                state.value = QueueState(
                    active = false,
                    paused = true,
                    workId = job.workId,
                    workTitle = job.title,
                    done = done,
                    total = total,
                    current = chapter.title,
                    errorMessage = "Sin conexión o AO3 no respondió",
                )
                updateNotification(job, done, total, paused = true)
                return false
            }
        }

        // Reconcile: every chapter is stored, so any leftover pending entry for
        // this work is stale (e.g. a cancel raced the last save). Leaving it
        // would make startIfPending re-run the whole queue on every cold start.
        // EXCEPT when a NEWER job for this same work is already queued (a
        // re-enqueue that landed while this run was finishing): its persisted
        // entry is alive, and wiping it here would drop the resumable queue if
        // the process dies before that job saves its first chapter.
        val superseded = synchronized(pending) { pending.any { it.workId == job.workId } }
        if (!superseded) store.removePendingJob(job.workId)
        if (cancelRequested) return true

        state.value = QueueState(
            active = false,
            workId = job.workId,
            workTitle = job.title,
            done = total,
            total = total,
            completedAt = System.currentTimeMillis(),
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTIFICATION_ID,
            baseNotification(job)
                .setContentTitle("Descarga completada")
                .setContentText(
                    "$total ${if (total == 1) "capítulo" else "capítulos"} guardados · ${job.title}",
                )
                .setProgress(0, 0, false)
                .setOngoing(false)
                .build(),
        )
        return true
    }

    private fun publishProgress(job: JobInfo, chapter: ChapterInfo, done: Int, total: Int) {
        state.value = state.value.copy(done = done, total = total, current = chapter.title)
        updateNotification(job, done, total, paused = false)
    }

    private fun updateNotification(job: JobInfo, done: Int, total: Int, paused: Boolean) {
        startForegroundCompat(
            baseNotification(job)
                .setContentTitle(if (paused) "Descarga pausada" else "Descargando: ${job.title}")
                .setContentText(
                    if (paused) "$done de $total · pulsa Reanudar desde la app"
                    else "Capítulo $done de $total",
                )
                .setProgress(total, done, false)
                .setOngoing(!paused)
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

    private fun baseNotification(job: JobInfo?): NotificationCompat.Builder {
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

    private fun Intent.toJob(): JobInfo? {
        val workId = getLongExtra(EXTRA_WORK_ID, -1L)
        val title = getStringExtra("title") ?: return null
        val raw = getStringExtra("chapters") ?: return null
        val chapters = runCatching { JSONArray(raw).toChapters() }.getOrElse { emptyList() }
        if (workId < 0 || chapters.isEmpty()) return null
        return JobInfo(workId, title, chapters)
    }

    private fun JobInfo.toPending(): Store.PendingJob = Store.PendingJob(workId, title, chapters)
    private fun Store.PendingJob.toJob(): JobInfo = JobInfo(workId, title, chapters)

    companion object {
        const val ACTION_DOWNLOAD = "net.spin.ao3.action.DOWNLOAD"
        const val ACTION_RESUME = "net.spin.ao3.action.RESUME"
        const val ACTION_CANCEL = "net.spin.ao3.action.CANCEL"
        const val EXTRA_WORK_ID = "workId"
        private const val CHANNEL_ID = "descargas"
        const val NOTIFICATION_ID = 42

        data class QueueState(
            val active: Boolean = false,
            val paused: Boolean = false,
            val workId: Long? = null,
            val workTitle: String = "",
            val done: Int = 0,
            val total: Int = 0,
            val current: String = "",
            val errorMessage: String? = null,
            val completedAt: Long = 0L,
            val cancelledAt: Long = 0L,
        )

        val state = MutableStateFlow(QueueState())
        private val consumedCompletions = ConcurrentHashMap.newKeySet<Long>()

        /** Atomically consumes an in-app completion event exactly once. */
        fun consumeCompletion(timestamp: Long): Boolean =
            timestamp > 0L && consumedCompletions.add(timestamp)

        data class JobInfo(
            val workId: Long,
            val title: String,
            val chapters: List<ChapterInfo>,
        )

        fun startIfPending(context: Context) {
            val app = context.applicationContext as Ao3App
            if (app.container.store.pendingJobs().isNotEmpty()) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadQueueService::class.java).apply { action = ACTION_RESUME },
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
            putExtra(EXTRA_WORK_ID, workId)
            putExtra("title", title)
            putExtra("chapters", chapters.toJsonArray().toString())
        }

        fun resume(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadQueueService::class.java).apply { action = ACTION_RESUME },
            )
        }

        fun cancel(context: Context, workId: Long? = null) {
            context.startService(
                Intent(context, DownloadQueueService::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra(EXTRA_WORK_ID, workId ?: -1L)
                },
            )
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
