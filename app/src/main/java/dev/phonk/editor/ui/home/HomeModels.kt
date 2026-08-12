package dev.phonk.editor.ui.home

import androidx.compose.ui.graphics.vector.ImageVector
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.util.TimeUtils.formatClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A single quick-action card on the home screen. */
data class QuickAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** A single AI tool card on the home screen. */
data class AiTool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Presentation model for a project card in the home carousel.
 * Backed by a real [PhonkProject] so the home stays data-driven and can be
 * wired to any future backend without UI changes.
 */
data class HomeProject(
    val id: String,
    val name: String,
    val durationLabel: String,
    val dateLabel: String,
    val sizeLabel: String,
    val videoUri: String?,
    /** Deterministic seed used to generate a stylized neon thumbnail when no
     *  real video frame is available yet. */
    val artSeed: Int,
) {
    companion object {
        private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        fun from(project: PhonkProject): HomeProject {
            val durMs = project.timelineDurationMs().takeIf { it > 0 } ?: project.videoDurationMs
            val duration = if (durMs > 0L) formatClock(durMs) else "—"
            val date = dateFormat.format(Date(project.updatedAt.takeIf { it > 0 } ?: project.createdAt))
            // Rough exported-size estimate at ~8 Mbps average video bitrate.
            val sizeMb = (durMs / 1000L * 1L).coerceAtLeast(1L)
            return HomeProject(
                id = project.id,
                name = project.name.ifBlank { "Untitled" },
                durationLabel = duration,
                dateLabel = date,
                sizeLabel = "${sizeMb}MB",
                videoUri = project.videoUri,
                artSeed = project.id.hashCode().and(Int.MAX_VALUE),
            )
        }
    }
}
