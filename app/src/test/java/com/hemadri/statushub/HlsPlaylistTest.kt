package com.hemadri.statushub

import com.hemadri.statushub.browser.HlsDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class HlsPlaylistTest {

    private val hls = HlsDownloader()
    private val base = "https://video.example.com/ext_tw_video/123/pu/pl/master.m3u8?tag=12"

    // X/Twitter-style master: fMP4 variants with a separate audio rendition
    private val master = """
        #EXTM3U
        #EXT-X-MEDIA:NAME="Audio",TYPE=AUDIO,GROUP-ID="audio-128000",AUTOSELECT=YES,URI="/ext_tw_video/123/pu/pl/mp4a/128000/a.m3u8"
        #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=300000,BANDWIDTH=400000,RESOLUTION=480x270,CODECS="mp4a.40.2,avc1.4d001e",AUDIO="audio-128000"
        /ext_tw_video/123/pu/pl/avc1/480x270/low.m3u8
        #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=2000000,BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="mp4a.40.2,avc1.640020",AUDIO="audio-128000"
        /ext_tw_video/123/pu/pl/avc1/1280x720/hd.m3u8
    """.trimIndent()

    @Test
    fun picksVariantsAndResolvesUrls() {
        val variants = hls.parseVariants(master, base)
        assertEquals(2, variants.size)
        val best = variants.maxBy { it.height ?: 0 }
        assertEquals(720, best.height)
        assertEquals("https://video.example.com/ext_tw_video/123/pu/pl/avc1/1280x720/hd.m3u8", best.uri)
        assertEquals("audio-128000", best.audioGroup)
        assertEquals(
            "https://video.example.com/ext_tw_video/123/pu/pl/mp4a/128000/a.m3u8",
            hls.parseAudioRendition(master, base, "audio-128000")
        )
    }

    @Test
    fun parsesFragmentedMp4MediaPlaylist() {
        val media = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:3.0,
            seg0.m4s
            #EXTINF:3.0,
            seg1.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        val playlist = hls.parseMediaPlaylist(media, "https://cdn.example.com/v/720/hd.m3u8")
        assertEquals("https://cdn.example.com/v/720/init.mp4", playlist.initUri)
        assertEquals(listOf("seg0.m4s", "seg1.m4s"), playlist.segments.map { it.uri.substringAfterLast('/') })
        assertNull(playlist.segments.first().key)
    }

    @Test
    fun parsesAesKeysByteRangesAndSequence() {
        val media = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:7
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k1",IV=0x000102030405060708090A0B0C0D0E0F
            #EXTINF:4,
            #EXT-X-BYTERANGE:1000@0
            all.ts
            #EXTINF:4,
            #EXT-X-BYTERANGE:500
            all.ts
        """.trimIndent()
        val playlist = hls.parseMediaPlaylist(media, "https://cdn.example.com/s/index.m3u8")
        assertNull(playlist.initUri)
        val (first, second) = playlist.segments
        assertEquals(0L until 1000L, first.byteRange)
        assertEquals(1000L until 1500L, second.byteRange) // continues after the previous range
        assertEquals(7L, first.sequence)
        assertEquals(8L, second.sequence)
        assertEquals("https://keys.example.com/k1", first.key!!.uri)
        assertEquals(15, first.key!!.iv!!.last().toInt())
    }

    @Test
    fun rejectsDrmStreams() {
        val media = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://drm"
            #EXTINF:4,
            a.ts
        """.trimIndent()
        assertThrows(IOException::class.java) { hls.parseMediaPlaylist(media, "https://cdn.example.com/x.m3u8") }
    }
}
