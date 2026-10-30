package moye.picax.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.CheckCircle
import io.github.neoruaa.glasmio.icons.GlasmioIconResolver
import io.github.neoruaa.glasmio.icons.MaterialGlasmioIconResolver

object PicaXIcons : GlasmioIconResolver {
    override fun resolve(systemName: String) = when {
        systemName.startsWith("text.bubble") -> Icons.Outlined.ChatBubbleOutline
        systemName.startsWith("doc.on.doc") -> Icons.Outlined.ContentCopy
        systemName.startsWith("square.and.arrow.up") -> Icons.Outlined.IosShare
        systemName.startsWith("square.and.arrow.down") -> Icons.Outlined.FileDownload
        systemName == "hand.thumbsup.fill" -> Icons.Filled.ThumbUp
        systemName.startsWith("hand.thumbsup") -> Icons.Outlined.ThumbUp
        systemName.startsWith("gauge") -> Icons.Outlined.Speed
        systemName.startsWith("eye.slash") -> Icons.Outlined.VisibilityOff
        systemName.startsWith("eye") -> Icons.Outlined.Visibility
        systemName.startsWith("person.2") -> Icons.Outlined.Group
        systemName.startsWith("checkmark.shield") -> Icons.Outlined.VerifiedUser
        systemName == "checkmark.circle.fill" -> Icons.Filled.CheckCircle
        systemName == "bookmark.fill" -> Icons.Filled.Bookmark
        systemName == "heart.fill" -> Icons.Filled.Favorite
        systemName.startsWith("character.book") -> Icons.Outlined.Translate
        systemName == "chevron.up.chevron.down" -> Icons.Outlined.UnfoldMore
        systemName.startsWith("line.3.horizontal.decrease") -> Icons.Outlined.FilterAlt
        systemName.startsWith("square.and.pencil") -> Icons.Outlined.Edit
        systemName.startsWith("doc.text") -> Icons.Outlined.Description
        systemName == "square.3.layers.3d" || systemName.startsWith("square.grid") -> Icons.Outlined.GridView
        systemName == "house" -> Icons.Outlined.Home
        systemName == "heart" -> Icons.Outlined.FavoriteBorder
        systemName == "safari" -> Icons.Outlined.Explore
        systemName.startsWith("network") -> Icons.Outlined.Language
        systemName.startsWith("bookmark") -> Icons.Outlined.BookmarkBorder
        systemName.startsWith("timer") -> Icons.Outlined.Timer
        systemName.startsWith("tag") -> Icons.Outlined.Sell
        systemName.startsWith("number") -> Icons.Outlined.Tag
        systemName.startsWith("shuffle") -> Icons.Outlined.Shuffle
        systemName.startsWith("internaldrive") -> Icons.Outlined.Storage
        systemName.startsWith("tray") -> Icons.Outlined.Inventory2
        systemName.startsWith("paintbrush") -> Icons.Outlined.Palette
        systemName.startsWith("flame") -> Icons.Outlined.LocalFireDepartment
        systemName.startsWith("chart") -> Icons.Outlined.BarChart
        systemName.startsWith("trophy") -> Icons.Outlined.EmojiEvents
        systemName.startsWith("link") -> Icons.Outlined.Link
        systemName.startsWith("pin") -> Icons.Outlined.PushPin
        else -> MaterialGlasmioIconResolver.resolve(systemName)
    }
}
