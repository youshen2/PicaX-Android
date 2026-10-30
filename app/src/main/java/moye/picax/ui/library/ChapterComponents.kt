package moye.picax.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.Chapter
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun ChapterSectionHeader(count: Int) {
    val app = LocalApp.current
    val data = appData()
    val descending = data.preferences["detail.chapterDescending"] == "true"
    SectionHeader("章节 $count") {
        ActionMenu(listOf(
            MenuAction("正序", "arrow.up", selected = !descending, onClick = { app.store.set("detail.chapterDescending", false) }),
            MenuAction("倒序", "arrow.down", selected = descending, onClick = { app.store.set("detail.chapterDescending", true) }),
        )) { open -> GlasmioIconButton(open, size = 28.dp) { SfIcon(if(descending) "arrow.up" else "arrow.down", "章节排序", size = 16.dp, tint = GlasmioTheme.colors.secondaryContent) } }
    }
}

@Composable
fun ChapterRow(chapter: Chapter, current: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SfIcon("doc.text", null, size = 22.dp, tint = if(current) GlasmioTheme.colors.accent else GlasmioTheme.colors.secondaryContent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(chapter.title, fontSize = 15.sp)
            if(chapter.subtitle.isNotBlank()) Text(chapter.subtitle, fontSize = 12.sp, color = GlasmioTheme.colors.secondaryContent)
        }
        if(current) SfIcon("checkmark", "阅读中", size = 16.dp, tint = GlasmioTheme.colors.accent)
    }
}
