package net.spin.ao3.data.model

/** Sort options for AO3 work searches. [column] = AO3 work_search[sort_column] value. */
enum class SortOption(val label: String, val column: String?) {
    BEST_MATCH("Mejor resultado", null),
    KUDOS("Más kudos", "kudos_count"),
    HITS("Más leídas", "hits"),
    COMMENTS("Más comentarios", "comments_count"),
    BOOKMARKS("Más marcadores", "bookmarks_count"),
    WORDS("Más largas", "word_count"),
    UPDATED("Recién actualizadas", "revised_at"),
    POSTED("Recién publicadas", "created_at"),
    TITLE("Título", "title_to_sort_on"),
    CREATOR("Autor", "authors_to_sort_on"),
}

/** A filter option with AO3's internal id. */
data class FilterOption(val id: Int, val label: String)

/** AO3 rating ids (include ratings are radios: pick one). */
val RATING_OPTIONS = listOf(
    FilterOption(9, "Not Rated"),
    FilterOption(10, "General Audiences"),
    FilterOption(11, "Teen And Up Audiences"),
    FilterOption(12, "Mature"),
    FilterOption(13, "Explicit"),
)

/** AO3 archive warning ids (multi-select checkboxes). */
val WARNING_OPTIONS = listOf(
    FilterOption(14, "Creator Chose Not To Use"),
    FilterOption(16, "No Archive Warnings Apply"),
    FilterOption(17, "Graphic Depictions Of Violence"),
    FilterOption(18, "Major Character Death"),
    FilterOption(19, "Rape/Non-Con"),
    FilterOption(20, "Underage Sex"),
)

/** AO3 category ids (multi-select checkboxes). */
val CATEGORY_OPTIONS = listOf(
    FilterOption(21, "Gen"),
    FilterOption(22, "F/M"),
    FilterOption(23, "M/M"),
    FilterOption(24, "Other"),
    FilterOption(116, "F/F"),
    FilterOption(2246, "Multi"),
)

/**
 * All the AO3 "Search and Filter" options. Mirrors the sidebar that AO3's
 * own UI submits to /works/search (GET).
 */
data class SearchFilters(
    val query: String = "",
    /** Browse a fandom/tag by name (sent as work_search[fandoms]). */
    val tag: String? = null,
    /** Comma-separated tags to include (work_search[other_tag_names], AND semantics). */
    val includeTags: String = "",
    /** Comma-separated tags to exclude (work_search[excluded_tag_names]). */
    val excludeTags: String = "",
    val rating: Int? = null,
    val warnings: Set<Int> = emptySet(),
    val categories: Set<Int> = emptySet(),
    val excludeRating: Int? = null,
    val excludeWarnings: Set<Int> = emptySet(),
    val excludeCategories: Set<Int> = emptySet(),
    val completeOnly: Boolean = false,
    val crossoverOnly: Boolean = false,
    val wordsFrom: String = "",
    val wordsTo: String = "",
    val dateFrom: String = "",
    val dateTo: String = "",
) {
    /** True when anything beyond the free-text query is set. */
    val hasFilters: Boolean
        get() = tag != null || includeTags.isNotBlank() || excludeTags.isNotBlank() ||
            rating != null || warnings.isNotEmpty() || categories.isNotEmpty() ||
            excludeRating != null || excludeWarnings.isNotEmpty() || excludeCategories.isNotEmpty() ||
            completeOnly || crossoverOnly || wordsFrom.isNotBlank() || wordsTo.isNotBlank() ||
            dateFrom.isNotBlank() || dateTo.isNotBlank()

    /** Compact serialization for navigation state (\u0002 separator). */
    fun serialize(): String = listOf(
        query,
        tag ?: "",
        includeTags,
        excludeTags,
        rating?.toString() ?: "",
        warnings.joinToString(","),
        categories.joinToString(","),
        excludeRating?.toString() ?: "",
        excludeWarnings.joinToString(","),
        excludeCategories.joinToString(","),
        if (completeOnly) "1" else "",
        if (crossoverOnly) "1" else "",
        wordsFrom,
        wordsTo,
        dateFrom,
        dateTo,
    ).joinToString("\u0002")

    companion object {
        fun parse(s: String): SearchFilters {
            val p = s.split("\u0002")
            fun g(i: Int) = p.getOrNull(i) ?: ""
            fun ids(i: Int) = g(i).split(",").mapNotNull { it.toIntOrNull() }.toSet()
            return SearchFilters(
                query = g(0),
                tag = g(1).ifEmpty { null },
                includeTags = g(2),
                excludeTags = g(3),
                rating = g(4).toIntOrNull(),
                warnings = ids(5),
                categories = ids(6),
                excludeRating = g(7).toIntOrNull(),
                excludeWarnings = ids(8),
                excludeCategories = ids(9),
                completeOnly = g(10) == "1",
                crossoverOnly = g(11) == "1",
                wordsFrom = g(12),
                wordsTo = g(13),
                dateFrom = g(14),
                dateTo = g(15),
            )
        }
    }
}

/** A work as shown in a search result (blurb). */
data class WorkSummary(
    val id: Long,
    val title: String,
    val author: String,
    val authorUrl: String?,
    val fandoms: List<String>,
    val rating: String?,
    val ratingKey: String?,
    val warnings: List<String>,
    val categories: List<String>,
    val otherTags: List<String>,
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val summary: String,
    val words: Long,
    val chapterCount: Int,
    val chapterTotal: Int?,
    val hits: Long,
    val kudos: Long,
    val comments: Long,
    val bookmarks: Long,
    val published: String?,
    val updated: String?,
    val url: String,
) {
    val isCompleted: Boolean get() = chapterTotal != null && chapterCount >= chapterTotal
}

/** A single chapter; [content] holds sanitized HTML when available (inline or downloaded). */
data class ChapterInfo(
    val index: Int,
    val title: String,
    val url: String?,
    val content: String? = null,
)

/** Full work detail page data. */
data class WorkDetail(
    val summary: WorkSummary,
    val descriptionHtml: String?,
    val notesHtml: String?,
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val additionalTags: List<String> = emptyList(),
    val chapters: List<ChapterInfo>,
)
