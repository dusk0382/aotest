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

/** AO3 language (work_search[language_id] uses ISO codes, not numeric ids). */
data class LanguageOption(val code: String, val label: String)

val LANGUAGE_OPTIONS = listOf(
    LanguageOption("en", "English"),
    LanguageOption("es", "Español"),
    LanguageOption("fr", "Français"),
    LanguageOption("de", "Deutsch"),
    LanguageOption("pt", "Português"),
    LanguageOption("it", "Italiano"),
    LanguageOption("nl", "Nederlands"),
    LanguageOption("pl", "Polski"),
    LanguageOption("ru", "Русский"),
    LanguageOption("zh", "中文"),
    LanguageOption("ja", "日本語"),
    LanguageOption("ko", "한국어"),
    LanguageOption("ar", "العربية"),
    LanguageOption("hi", "हिन्दी"),
    LanguageOption("tr", "Türkçe"),
    LanguageOption("sv", "Svenska"),
    LanguageOption("da", "Dansk"),
    LanguageOption("no", "Norsk"),
    LanguageOption("fi", "suomi"),
    LanguageOption("el", "Ελληνικά"),
    LanguageOption("cs", "Čeština"),
    LanguageOption("uk", "Українська"),
    LanguageOption("vi", "Tiếng Việt"),
    LanguageOption("th", "ไทย"),
    LanguageOption("id", "Bahasa Indonesia"),
    LanguageOption("fil", "Filipino"),
    LanguageOption("hu", "Magyar"),
    LanguageOption("ro", "Română"),
    LanguageOption("he", "עברית"),
)

/**
 * All the AO3 "Search and Filter" options. Mirrors the sidebar that AO3's
 * own UI submits to /works (GET, with a canonical tag_id).
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
    /** work_search[crossover]=F — hides works with more than one fandom. */
    val excludeCrossover: Boolean = false,
    /** work_search[series_count]=1 — only works that are part of a series. */
    val partOfSeries: Boolean = false,
    /** ISO code, work_search[language_id]. */
    val language: String? = null,
    val wordsFrom: String = "",
    val wordsTo: String = "",
    val dateFrom: String = "",
    val dateTo: String = "",
    /** AO3 tag ids for the sidebar facets (fandom/character/relationship/freeform). */
    val fandomIds: Set<Long> = emptySet(),
    val characterIds: Set<Long> = emptySet(),
    val relationshipIds: Set<Long> = emptySet(),
    val freeformIds: Set<Long> = emptySet(),
    val excludeFandomIds: Set<Long> = emptySet(),
    val excludeCharacterIds: Set<Long> = emptySet(),
    val excludeRelationshipIds: Set<Long> = emptySet(),
    val excludeFreeformIds: Set<Long> = emptySet(),
) {
    /** True when anything beyond the free-text query is set. */
    val hasFilters: Boolean
        get() = tag != null || includeTags.isNotBlank() || excludeTags.isNotBlank() ||
            rating != null || warnings.isNotEmpty() || categories.isNotEmpty() ||
            excludeRating != null || excludeWarnings.isNotEmpty() || excludeCategories.isNotEmpty() ||
            completeOnly || crossoverOnly || excludeCrossover || partOfSeries || language != null ||
            wordsFrom.isNotBlank() || wordsTo.isNotBlank() ||
            dateFrom.isNotBlank() || dateTo.isNotBlank() ||
            fandomIds.isNotEmpty() || characterIds.isNotEmpty() ||
            relationshipIds.isNotEmpty() || freeformIds.isNotEmpty() ||
            excludeFandomIds.isNotEmpty() || excludeCharacterIds.isNotEmpty() ||
            excludeRelationshipIds.isNotEmpty() || excludeFreeformIds.isNotEmpty()

    /**
     * Compact serialization for navigation state. Every field is URL-encoded
     * and joined with a \u0002 separator, so a query containing the separator
     * (or any other character) round-trips safely. See [urlEncode]/[urlDecode].
     *
     * The id sets are SORTED before joining so that two filter objects holding
     * the same sets in a different insertion order (a Set is order-agnostic)
     * always produce the same key — this string doubles as a cache key.
     */
    fun serialize(): String = listOf(
        query,
        tag ?: "",
        includeTags,
        excludeTags,
        rating?.toString() ?: "",
        warnings.sorted().joinToString(","),
        categories.sorted().joinToString(","),
        excludeRating?.toString() ?: "",
        excludeWarnings.sorted().joinToString(","),
        excludeCategories.sorted().joinToString(","),
        if (completeOnly) "1" else "",
        if (crossoverOnly) "1" else "",
        if (excludeCrossover) "1" else "",
        if (partOfSeries) "1" else "",
        language ?: "",
        wordsFrom,
        wordsTo,
        dateFrom,
        dateTo,
        fandomIds.sorted().joinToString(","),
        characterIds.sorted().joinToString(","),
        relationshipIds.sorted().joinToString(","),
        freeformIds.sorted().joinToString(","),
        excludeFandomIds.sorted().joinToString(","),
        excludeCharacterIds.sorted().joinToString(","),
        excludeRelationshipIds.sorted().joinToString(","),
        excludeFreeformIds.sorted().joinToString(","),
    ).joinToString("\u0002") { urlEncode(it) }

    companion object {
        fun parse(s: String): SearchFilters {
            val p = s.split("\u0002")
            fun g(i: Int) = urlDecode(p.getOrNull(i) ?: "")
            fun ids(i: Int) = g(i).split(",").mapNotNull { it.toIntOrNull() }.toSet()
            fun longIds(i: Int) = g(i).split(",").mapNotNull { it.toLongOrNull() }.toSet()
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
                excludeCrossover = g(12) == "1",
                partOfSeries = g(13) == "1",
                language = g(14).ifEmpty { null },
                wordsFrom = g(15),
                wordsTo = g(16),
                dateFrom = g(17),
                dateTo = g(18),
                fandomIds = longIds(19),
                characterIds = longIds(20),
                relationshipIds = longIds(21),
                freeformIds = longIds(22),
                excludeFandomIds = longIds(23),
                excludeCharacterIds = longIds(24),
                excludeRelationshipIds = longIds(25),
                excludeFreeformIds = longIds(26),
            )
        }
    }
}

/** A sidebar facet option: AO3 tag id + label + result count. */
data class FacetItem(
    val id: Long,
    val label: String,
    val count: Int = 0,
)

/** The kind of a filter sidebar group (maps to its `_ids[]` query params). */
enum class FacetKind { RATING, WARNING, CATEGORY, FANDOM, CHARACTER, RELATIONSHIP, FREEFORM }

/** One include/exclude pair from AO3's "Filters" sidebar. */
data class FacetGroup(
    val kind: FacetKind,
    val name: String,
    val include: List<FacetItem> = emptyList(),
    val exclude: List<FacetItem> = emptyList(),
)

/** The "Filters" sidebar of an AO3 tag/works listing. */
data class FilterFacets(
    val groups: List<FacetGroup> = emptyList(),
)

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

/**
 * A single chapter; [content] holds sanitized HTML when available (inline or
 * downloaded) and [chapterId] is AO3's numeric chapter id (used for comments).
 */
data class ChapterInfo(
    val index: Int,
    val title: String,
    val url: String?,
    val chapterId: Long? = null,
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

/** A single comment in a thread (flat list; [depth] drives indentation). */
data class Ao3Comment(
    val id: Long,
    val author: String,
    val authorUrl: String?,
    val date: String,
    /** Sanitized HTML of the comment body (rendered in the reader WebView style). */
    val html: String,
    /** Plain-text version of the body. */
    val text: String,
    val depth: Int,
    /** Commenter's icon (img.icon from the thread), null when unset/default. */
    val avatarUrl: String? = null,
)

/** A user/pseud profile on AO3 (bio, registration date and works). */
data class AuthorProfile(
    val username: String,
    val displayName: String,
    /** "2010-01-12" or null when the page doesn't show it. */
    val joined: String? = null,
    val pseuds: List<String> = emptyList(),
    val bio: String = "",
    /** The user's profile icon (img.icon on the profile page), absolute URL. */
    val avatarUrl: String? = null,
)

/**
 * The author's works list page (/users/{name}/works): total count from the
 * heading + the first page of blurbs (AO3 paginates by 20). Loaded separately
 * from [AuthorProfile] so the profile header can render before the works.
 */
data class AuthorWorks(
    /** Number of works, when the works page shows it ("11 Works by …"). */
    val count: Int?,
    val works: List<WorkSummary>,
)

/** Info parsed from a work's Atom feed (used to check for updates without the HTML). */
data class WorkFeedInfo(
    /** Number of <entry> elements (published chapters). */
    val chapterCount: Int,
    /** ISO-8601 last-updated timestamp from the feed, when present. */
    val updated: String?,
)

/**
 * URL-encodes a field for safe embedding in a serialized route/filter string.
 * Control characters (like the \u0001/\u0002 separators) become %XX, so a user
 * query can never break the round-trip. Shared by [SearchFilters.serialize]
 * and [net.spin.ao3.ui.Route.serialize].
 */
internal fun urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

/** Inverse of [urlEncode]; falls back to the raw string on malformed input. */
internal fun urlDecode(s: String): String =
    runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
