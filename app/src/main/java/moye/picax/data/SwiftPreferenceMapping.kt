package moye.picax.data

import org.json.JSONObject

internal object SwiftPreferenceMapping {
    fun read(values: Map<String, JSONObject>): Map<String, String> {
        val reader = mapOf(
            "progressStyle" to "progressStyle", "progressPosition" to "progressPosition", "showsPageLabel" to "pageLabel",
            "progressFollowsUIVisibility" to "progressFollow", "progressTapSelectionEnabled" to "progressTap",
            "progressBackgroundOpacity" to "progressOpacity", "progressBottomInset" to "progressInset", "readingMode" to "mode",
            "wholeBookContinuousReading" to "whole", "multiBookContinuousReading" to "multi", "wholeBookContinuesReadingList" to "nextBook",
            "imageSpacing" to "gap", "firstImageTopPadding" to "top", "lastImageBottomPadding" to "bottom", "preloadImageCount" to "preload",
            "preloadsNextChapterNearEnd" to "nextPreload", "nextChapterPreloadPageThreshold" to "nearEnd", "pagedPreloadDelay" to "pagedDelay",
            "imageRetryCount" to "retry", "imageRetryInterval" to "retryDelay", "reducesImageBrightnessInDarkMode" to "dim", "hidesStatusBar" to "hideStatus",
            "uiToggleMode" to "uiToggle", "tapPagingEnabled" to "tap", "tapPagingInverted" to "tapInverted", "tapPagingEdgePercent" to "tapEdge",
            "tapPagingDistancePercent" to "tapDistance", "pinchZoomEnabled" to "zoomPinch", "doubleTapZoomEnabled" to "zoomDouble",
            "doubleTapZoomScale" to "doubleScale", "longPressZoomEnabled" to "zoomLong", "longPressZoomScale" to "longScale",
            "longPressZoomTriggerDuration" to "longDelay", "autoPagingInterval" to "autoSeconds", "autoPagingDistancePercent" to "autoDistance",
            "smoothContinuousAutoPaging" to "smooth", "autoPagingTurnsChapter" to "autoChapter", "showsNextChapterButtonAtEnd" to "endButton",
            "chapterEndButtonPosition" to "endPosition", "chapterEndButtonHorizontalInset" to "endHorizontal", "chapterEndButtonVerticalInset" to "endVertical",
            "nextChapterButtonSwitchesBooks" to "endNextBook", "showsChapterCommentsAtEnd" to "comments", "showsSystemStatus" to "status",
            "systemStatusFollowsUIVisibility" to "statusFollow", "systemStatusStyle" to "statusStyle", "systemStatusPosition" to "statusPosition",
            "systemStatusBottomInset" to "statusInset", "usesProgressGlassBackground" to "progressGlass", "usesSystemStatusGlassBackground" to "statusGlass",
            "showsReadingListBookToast" to "bookToast", "readingListAutoAdvancesAtBoundary" to "listAuto",
            "autoBurnAfterReadingSingle" to "burnSingle", "autoBurnAfterReadingList" to "burnList"
        ).mapKeys { "settings.reader.${it.key}" }.mapValues { "reader.${it.value}" }
        val other = mapOf(
            "settings.home.showsHistorySection" to "home.history", "settings.home.showsReadLaterSection" to "home.later",
            "settings.home.showsReadingDurationSection" to "home.time", "settings.home.showsDownloadSection" to "home.downloads",
            "settings.home.showsAccountManagementEntry" to "home.accounts", "settings.explore.defaultPlatform" to "explore.platform",
            "settings.readFilter.hidesReadComicsInLists" to "list.hideRead", "settings.readFilter.hiddenProgressThreshold" to "list.readThreshold",
            "settings.readingDuration.minimumSessionSeconds" to "time.minimum", "settings.search.enablesSearchSuggestions" to "search.suggestions",
            "settings.search.translatesChineseSearchTerms" to "search.translate", "settings.search.focusesSearchFieldOnOpen" to "search.focus",
            "settings.appBehavior.checksClipboardForComicLinks" to "behavior.clipboard", "settings.appBehavior.checksClipboardOnlyOnLaunch" to "behavior.clipboardLaunchOnly"
        )
        return (reader + other).mapNotNull { (source, target) ->
            val item = values[source] ?: return@mapNotNull null
            var value = listOf("stringValue", "boolValue", "intValue", "doubleValue").firstNotNullOfOrNull { key -> item.opt(key)?.takeUnless { it == JSONObject.NULL } }?.toString() ?: return@mapNotNull null
            value = when(target) {
                "reader.mode" -> mapOf("topToBottomContinuous" to "continuous", "topToBottom" to "vertical", "leftToRight" to "ltr", "rightToLeft" to "rtl", "pageCurl" to "curl")[value] ?: value
                "reader.doubleScale", "reader.longScale", "reader.progressOpacity" -> (value.toDouble() * 100).toInt().toString()
                "reader.pagedDelay", "reader.retryDelay", "reader.longDelay" -> (value.toDouble() * 1000).toInt().toString()
                else -> value.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()?.toString() ?: value
            }
            target to value
        }.toMap()
    }
}
