package com.hemadri.statushub.instagram

import java.math.BigInteger

/**
 * Parses Instagram links (posts, reels, IGTV, stories, highlights, share links)
 */
object InstagramLinks {

    sealed class Target {
        data class Post(val shortcode: String) : Target()
        data class Story(val username: String, val storyId: String) : Target()
        data class Highlight(val highlightId: String) : Target()
        /** instagram.com/share/... links redirect to the real post URL */
        data class Share(val url: String) : Target()
    }

    private val URL_IN_TEXT = Regex("""https?://\S+""")
    private val POST = Regex("""instagram\.com/(?:[A-Za-z0-9_.]+/)?(?:p|reels?|tv)/([A-Za-z0-9_-]+)""")
    private val STORY = Regex("""instagram\.com/stories/([A-Za-z0-9_.]+)/(\d+)""")
    private val HIGHLIGHT = Regex("""instagram\.com/stories/highlights/(\d+)""")
    private val SHARE = Regex("""instagram\.com/share/""")

    private const val SHORTCODE_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun isInstagramUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("instagram.com/") || lower.contains("instagr.am/")
    }

    /** Finds the first Instagram link inside shared/copied text (captions often wrap the link) */
    fun findInstagramUrl(text: String): String? =
        URL_IN_TEXT.findAll(text)
            .map { it.value.trimEnd('.', ',', ')', ']', '"', '\'') }
            .firstOrNull { isInstagramUrl(it) }

    fun parse(url: String): Target? {
        val normalized = url.replace("instagr.am/", "instagram.com/")
        HIGHLIGHT.find(normalized)?.let { return Target.Highlight(it.groupValues[1]) }
        STORY.find(normalized)?.let { return Target.Story(it.groupValues[1], it.groupValues[2]) }
        // Before POST: "/share/reel/<id>" would otherwise look like user "share"'s reel
        if (SHARE.containsMatchIn(normalized)) return Target.Share(url)
        POST.find(normalized)?.let { return Target.Post(it.groupValues[1]) }
        return null
    }

    /**
     * Converts a post shortcode to its numeric media id.
     * Private-post shortcodes carry 28 extra characters that are not part of the id.
     */
    fun shortcodeToMediaId(shortcode: String): String {
        val code = if (shortcode.length > 28) shortcode.dropLast(28) else shortcode
        var id = BigInteger.ZERO
        val base = BigInteger.valueOf(64)
        for (c in code) {
            val index = SHORTCODE_ALPHABET.indexOf(c)
            require(index >= 0) { "Invalid shortcode" }
            id = id.multiply(base).add(BigInteger.valueOf(index.toLong()))
        }
        return id.toString()
    }
}
