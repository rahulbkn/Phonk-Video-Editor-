package dev.phonk.editor.ui.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.ui.components.SectionHeader

@Composable
fun RatioPanel(
    selectedAspect: String,
    onAspectSelected: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader("Aspect Ratio")
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("1:1", "9:16", "16:9", "4:5", "2.35:1").forEach { a ->
                val sel = selectedAspect == a
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) scheme.primary.copy(alpha = 0.25f) else scheme.surfaceVariant.copy(alpha = 0.7f))
                        .border(1.dp, if (sel) scheme.primary else scheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { onAspectSelected(a) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        a,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
