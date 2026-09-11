package com.flowframe.app.core.model

/** Completed/canceled/failed attempts are immutable; retry creates a new attempt. */
object TaskTransitions {
    private val terminal = setOf(TaskStage.COMPLETED, TaskStage.CANCELED, TaskStage.FAILED)
    fun apply(current: DownloadTask, proposed: DownloadTask): DownloadTask =
        if (current.stage in terminal) current else proposed
}
