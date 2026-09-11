package com.flowframe.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTransitionsTest {
    private val task = DownloadTask("attempt", "https://b23.tv/example", Platform.BILIBILI,
        "work", "Example", preset = QualityPreset.RECOMMENDED)

    @Test fun lateProgressCannotResurrectCanceledTask() {
        val canceled = task.copy(stage = TaskStage.CANCELED)
        val updated = TaskTransitions.apply(canceled, task.copy(stage = TaskStage.DOWNLOADING, progress = .8f))
        assertEquals(canceled, updated)
    }

    @Test fun cancelWinningPublicationRaceDoesNotAcquireOutput() {
        val canceled = task.copy(stage = TaskStage.CANCELED)
        val completed = task.copy(stage = TaskStage.COMPLETED, outputLocations = listOf("content://media/video/42"))
        assertEquals(canceled, TaskTransitions.apply(canceled, completed))
    }

    @Test fun completedMediaIsNotChangedByLateCancel() {
        val completed = task.copy(stage = TaskStage.COMPLETED, outputLocation = "content://media/video/42")
        assertEquals(completed, TaskTransitions.apply(completed, completed.copy(stage = TaskStage.CANCELED)))
    }

    @Test fun networkWaitCanResumeAnActiveAttempt() {
        val waiting = task.copy(stage = TaskStage.QUEUED)
        assertEquals(TaskStage.RESOLVING, TaskTransitions.apply(waiting, waiting.copy(stage = TaskStage.RESOLVING)).stage)
    }
}
