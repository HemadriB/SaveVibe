package com.hemadri.statushub

import com.hemadri.statushub.instagram.InstagramLinks
import com.hemadri.statushub.instagram.InstagramLinks.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramLinksTest {

    @Test
    fun parsesPostReelAndTvLinks() {
        assertEquals(Target.Post("Chunk8-jurw"), InstagramLinks.parse("https://www.instagram.com/reel/Chunk8-jurw/?igsh=abc"))
        assertEquals(Target.Post("BQ0eAlwhDrw"), InstagramLinks.parse("https://instagram.com/p/BQ0eAlwhDrw/"))
        assertEquals(Target.Post("Cop84x6u7CP"), InstagramLinks.parse("https://www.instagram.com/reels/Cop84x6u7CP/"))
        assertEquals(Target.Post("CDUMkliABpa"), InstagramLinks.parse("https://www.instagram.com/tv/CDUMkliABpa"))
        assertEquals(Target.Post("CDUMkliABpa"), InstagramLinks.parse("https://www.instagram.com/someuser/p/CDUMkliABpa/"))
        assertEquals(Target.Post("9o6LshA7zy"), InstagramLinks.parse("http://instagr.am/p/9o6LshA7zy/"))
    }

    @Test
    fun parsesStoriesHighlightsAndShareLinks() {
        assertEquals(
            Target.Story("natgeo", "3412345678901234567"),
            InstagramLinks.parse("https://www.instagram.com/stories/natgeo/3412345678901234567/?utm_source=ig_story_item_share")
        )
        assertEquals(
            Target.Highlight("17983407089364361"),
            InstagramLinks.parse("https://www.instagram.com/stories/highlights/17983407089364361/")
        )
        val share = "https://www.instagram.com/share/reel/BAB1cDeFgH/"
        assertEquals(Target.Share(share), InstagramLinks.parse(share))
        assertNull(InstagramLinks.parse("https://www.instagram.com/natgeo/"))
    }

    @Test
    fun convertsShortcodeToMediaId() {
        assertEquals("2913440072144448240", InstagramLinks.shortcodeToMediaId("Chunk8-jurw"))
        // Private-post shortcodes carry 28 extra characters
        assertEquals("2913440072144448240", InstagramLinks.shortcodeToMediaId("Chunk8-jurw" + "B".repeat(28)))
    }

    @Test
    fun findsInstagramLinkInSharedText() {
        assertEquals(
            "https://www.instagram.com/reel/Chunk8-jurw/?igsh=MWQ1",
            InstagramLinks.findInstagramUrl("Check this out! https://www.instagram.com/reel/Chunk8-jurw/?igsh=MWQ1")
        )
        assertNull(InstagramLinks.findInstagramUrl("https://example.com/video.mp4"))
        assertTrue(InstagramLinks.isInstagramUrl("https://instagr.am/p/abc/"))
        assertFalse(InstagramLinks.isInstagramUrl("https://youtube.com/watch?v=x"))
    }
}
