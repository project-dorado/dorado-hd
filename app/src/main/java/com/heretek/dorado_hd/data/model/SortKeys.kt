package com.heretek.dorado_hd.data.model

/**
 * Library sort-key normalization. The device normalized titles by stripping
 * leading articles and locale variants before indexing (device `zmedia_serv`
 * article-strip tables: `the ;an ;a ;…`). Pure so it is unit-testable.
 */
object SortKeys {
    private val ARTICLES = listOf("the ", "an ", "a ")

    /** Lowercased, leading-article-stripped key for stable A–Z ordering. */
    fun normalized(title: String): String {
        val lower = title.trim().lowercase()
        val stripped = ARTICLES.firstOrNull { lower.startsWith(it) }
            ?.let { lower.removePrefix(it) }
            ?: lower
        return stripped.trim()
    }
}
