package moye.picax.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.scaffold.rememberGlasmioHeaderScrollEdge
import io.github.neoruaa.glasmio.ui.*

/** Keeps the Glasmio modal mounted through dismissal and uses its own detents/insets. */
@Composable
fun AppSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    medium: Boolean = true,
    closeLabel: String = "完成",
    closeIcon: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val list = rememberLazyListState()
    val sheetState = rememberGlasmioSheetState(skipPartiallyExpanded = !medium)
    GlasmioBottomSheet(
        visible = visible,
        title = title,
        onDismissRequest = onDismiss,
        skipPartiallyExpanded = !medium,
        sheetState = sheetState,
        topBarBlurProgress = rememberGlasmioHeaderScrollEdge(list),
        topBar = {
            GlasmioSheetTopToolbar(title, actions = {
                actions()
                if(closeIcon) IconAction("xmark", "关闭", onDismiss)
                else GlasmioSheetButton(onDismiss) { Text(closeLabel) }
            })
        },
    ) {
        val insets = LocalGlasmioSheetContentPadding.current
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f),
                state = list,
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = insets.calculateTopPadding() + 12.dp,
                    bottom = if(footer == null) insets.calculateBottomPadding() + 20.dp else 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            if(footer != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = insets.calculateBottomPadding() + 12.dp)) { footer() }
            }
        }
    }
}

@Composable
fun ChoiceMenuRow(title: String, selected: String, options: List<Pair<String, String>>, onSelected: (String) -> Unit, symbol: String? = null, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    GlasmioMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        itemCount = options.size,
        morphFromAnchor = false,
        anchor = { open ->
            GlasmioListRow(title, systemName = symbol, enabled = enabled, onClick = open, trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(options.firstOrNull { it.first == selected }?.second.orEmpty(), color = GlasmioTheme.colors.secondaryContent)
                    SfIcon("chevron.up.chevron.down", null, size = 14.dp, tint = GlasmioTheme.colors.secondaryContent)
                }
            })
        },
    ) { backdrop, close ->
        options.forEach { (value, label) ->
            GlasmioMenuItem(label, { onSelected(value); close() }, systemName = if(selected == value) "checkmark" else null, backdrop = backdrop)
        }
    }
}

data class MenuAction(
    val title: String,
    val icon: String? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ActionMenu(
    actions: List<MenuAction>,
    morphFromAnchor: Boolean = true,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    GlasmioMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        itemCount = actions.size,
        morphFromAnchor = morphFromAnchor,
        anchor = anchor,
    ) { backdrop, close ->
        actions.forEach { action ->
            GlasmioMenuItem(
                action.title,
                { action.onClick(); close() },
                systemName = if(action.selected) "checkmark" else action.icon,
                enabled = action.enabled,
                destructive = action.destructive,
                backdrop = backdrop,
            )
        }
    }
}
