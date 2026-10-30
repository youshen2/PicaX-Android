package moye.picax.ui.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.normalizedOrder
import moye.picax.ui.*
import moye.picax.ui.components.*
import kotlin.math.roundToInt

@Composable
fun OrderPreference(title: String, key: String, labels: Map<String, String>) {
    val store = LocalApp.current.store
    val preferences by store.preferences.collectAsState()
    val order = normalizedOrder(preferences[key], labels.keys)
    val rowHeight = with(LocalDensity.current) { 52.dp.toPx() }
    fun move(id: String, target: Int) {
        val updated = order.toMutableList(); updated.remove(id); updated.add(target.coerceIn(0, updated.size), id); store.set(key, updated.joinToString(","))
    }
    Group(title, "长按拖动排序；也可点按右侧菜单调整。") {
        order.forEachIndexed { index, id ->
            var displacement by remember(id) { mutableFloatStateOf(0f) }
            ActionMenu(listOf(
                MenuAction("上移", "chevron.up", enabled = index > 0, onClick = { move(id, index - 1) }),
                MenuAction("下移", "chevron.down", enabled = index < order.lastIndex, onClick = { move(id, index + 1) }),
            )) { open ->
                GlasmioListRow(labels.getValue(id), modifier = Modifier.zIndex(if(displacement == 0f) 0f else 1f).graphicsLayer { translationY = displacement }
                    .pointerInput(id, order) { detectDragGesturesAfterLongPress(
                        onDragCancel = { displacement = 0f },
                        onDragEnd = { move(id, index + (displacement / rowHeight).roundToInt()); displacement = 0f },
                        onDrag = { change, amount -> change.consume(); displacement += amount.y }) },
                    trailing = { GlasmioIconButton(open, size = 32.dp) { SfIcon("line.3.horizontal", "调整${labels[id]}位置", size = 18.dp) } })
            }
        }
        GlasmioListRow("恢复默认排序", onClick = { store.set(key, labels.keys.joinToString(",")) })
    }
}
