package dev.phonk.editor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.ui.components.NavTab

/**
 * Templates screen — ready-made style presets. Applying a template creates a
 * real project (named after the preset) and opens the editor.
 */
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onOpen: (PhonkProject) -> Unit,
    onNavigate: (NavTab) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = homePalette()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var applied by remember { mutableStateOf<PhonkProject?>(null) }

    val filtered = remember(templates, query, category) {
        templates.filter { t ->
            val matchesCategory = category == "All" || t.category == category
            val matchesQuery = query.isBlank() ||
                t.name.contains(query, ignoreCase = true) ||
                t.description.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(top = topInset),
    ) {
        // ─── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = palette.text,
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.templates_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.text,
                )
                Text(
                    text = stringResource(R.string.templates_subtitle),
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                )
            }
        }

        // ─── Search ────────────────────────────────────────────────────────
        SearchField(query = query, onQueryChange = { query = it })
        Spacer(Modifier.height(12.dp))

        // ─── Categories ────────────────────────────────────────────────────
        val categories = listOf("All", "Phonk", "Beat", "Dark", "Glitch", "Retro")
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(categories.size) { i ->
                val c = categories[i]
                val selected = c == category
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (selected) palette.primary.copy(alpha = 0.22f) else palette.card)
                        .border(
                            1.dp,
                            if (selected) palette.primary else palette.border,
                            RoundedCornerShape(99.dp),
                        )
                        .clickable { category = c }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = c,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) palette.primaryBright else palette.textSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // ─── Grid ──────────────────────────────────────────────────────
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.templates_empty),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.templates_empty_desc),
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomInset + 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            onUse = { applied = applyTemplate(template) },
                        )
                    }
                }
            }

            // ─── Bottom navigation (floating, fixed) ───────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = bottomInset + 6.dp),
            ) {
                BottomNav(
                    activeTab = NavTab.TEMPLATES,
                    onTabSelected = { tab ->
                        if (tab == NavTab.TEMPLATES) return@BottomNav
                        when (tab) {
                            NavTab.HOME -> onBack()
                            else -> onNavigate(tab)
                        }
                    },
                )
            }
        }
    }

    // ─── Applied → open editor ────────────────────────────────────────────
    applied?.let { project ->
        androidx.compose.runtime.LaunchedEffect(project.id) {
            onOpen(project)
            applied = null
        }
    }
}

/** Real, self-contained template presets (metadata only; editor drives behaviour). */
data class TemplatePreset(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val accent: Color,
)

private val templates = listOf(
    TemplatePreset("drift", "Night Drift", "Phonk", "Low & slow phonk cruise", Color(0xFFA83FFF)),
    TemplatePreset("bass", "Bass Heavy", "Beat", "Punchy cut-on-drop edits", Color(0xFFC45CFF)),
    TemplatePreset("vhs", "VHS Horror", "Dark", "Grainy retro night vibes", Color(0xFF9A35E8)),
    TemplatePreset("static", "Static Glitch", "Glitch", "Hard glitch transitions", Color(0xFF19D98B)),
    TemplatePreset("retro", "Retro Drive", "Retro", "80s synthwave drive", Color(0xFFFF5DA2)),
    TemplatePreset("dark", "Dark Pulse", "Dark", "Minimal dark beat cuts", Color(0xFF6C5CE7)),
    TemplatePreset("crush", "Bit Crush", "Phonk", "Lo-fi crunch and stutter", Color(0xFFFD9644)),
    TemplatePreset("drop", "Drop Zone", "Beat", "Hit the drop every time", Color(0xFF00CEC9)),
)

private fun applyTemplate(template: TemplatePreset): PhonkProject = PhonkProject(
    name = template.name,
    beatSync = true,
    bpm = 140.0,
)

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val palette = homePalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = palette.text,
                fontSize = 15.sp,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TemplateCard(
    template: TemplatePreset,
    onUse: () -> Unit,
) {
    val palette = homePalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) HomeTokens.PressScale else 1f,
        label = "templateScale",
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(HomeTokens.CardCorner))
            .background(palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(HomeTokens.CardCorner))
            .clickable(interactionSource = interaction, indication = null, onClick = onUse),
    ) {
        // Stylized thumbnail
        Box(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            PhonkArtwork(
                modifier = Modifier.fillMaxSize(),
                seed = template.id.hashCode(),
                accent = template.accent,
                accentBright = template.accent,
            )
            Box(
                contentAlignment = Alignment.TopStart,
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Text(
                    text = template.category.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = template.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = template.description,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(template.accent.copy(alpha = 0.18f))
                    .clickable(onClick = onUse)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.templates_use),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = template.accent,
                )
            }
        }
    }
}
