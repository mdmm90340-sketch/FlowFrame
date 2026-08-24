package com.flowframe.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.flowframe.app.MainActivity
import com.flowframe.app.R
import com.flowframe.app.core.model.DownloadTask
import com.flowframe.app.core.model.GalleryOutputMode
import com.flowframe.app.core.model.MediaKind
import com.flowframe.app.core.model.TaskStage

object DownloadNotifications {
    const val CHANNEL_ID = "flowframe_downloads"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    fun foregroundInfo(context: Context, task: DownloadTask): ForegroundInfo {
        val notification = notification(context, task)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                task.id.hashCode(),
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(task.id.hashCode(), notification)
        }
    }

    fun update(context: Context, task: DownloadTask) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification(context, task))
    }

    private fun notification(context: Context, task: DownloadTask): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TASKS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val progress = (task.progress.coerceIn(0f, 1f) * 100).toInt()
        val content = when (task.stage) {
            TaskStage.QUEUED -> "等待开始"
            TaskStage.RESOLVING -> "正在准备"
            TaskStage.DOWNLOADING -> "正在下载 · $progress%"
            TaskStage.MERGING -> if (task.mediaKind == MediaKind.GALLERY) {
                "正在合成图文视频"
            } else {
                "正在合并音视频"
            }
            TaskStage.COMPLETED -> if (
                task.mediaKind == MediaKind.GALLERY && task.galleryOutputMode == GalleryOutputMode.IMAGES
            ) {
                "已保存 ${task.resolvedOutputLocations.size} 张图片"
            } else {
                "下载完成"
            }
            TaskStage.FAILED -> task.errorMessage ?: "下载失败"
            TaskStage.CANCELED -> "已取消"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(task.title)
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(task.stage in setOf(TaskStage.RESOLVING, TaskStage.DOWNLOADING, TaskStage.MERGING))
            .setAutoCancel(task.stage in setOf(TaskStage.COMPLETED, TaskStage.FAILED, TaskStage.CANCELED))
            .setProgress(100, progress, task.stage in setOf(TaskStage.QUEUED, TaskStage.RESOLVING, TaskStage.MERGING))
            .build()
    }
}
