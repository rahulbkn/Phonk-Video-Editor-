package dev.phonk.editor.ui.components

import androidx.annotation.StringRes
import dev.phonk.editor.R

/**
 * The five bottom-navigation destinations. This is the single source of
 * truth for tabs — every tab screen (Home, Templates, Projects, Profile)
 * renders the same [BottomNav] with the same set of items.
 */
enum class NavTab(@StringRes val labelRes: Int) {
    HOME(R.string.nav_home),
    TEMPLATES(R.string.nav_templates),
    CREATE(R.string.nav_create),
    PROJECTS(R.string.nav_projects),
    PROFILE(R.string.nav_profile),
}
