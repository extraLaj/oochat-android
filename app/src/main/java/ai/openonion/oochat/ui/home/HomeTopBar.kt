package ai.openonion.oochat.ui.home

import ai.openonion.oochat.ui.components.BackTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/**
 * The title bar of an agent's Home page, shared by the Dashboard and the Control Center. It always
 * has the way back: to the app's own Dashboard it was opened from, wherever that is (W16AAPPLE-115).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopBar(title: String, onNavigateBack: () -> Unit) {
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    BackTopAppBar(title = title, onNavigateBack = onNavigateBack, colors = colors)
}
