package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InstagramMetaParserTest {

    @Test
    fun `parses order-independent OG tags and prefers uncropped full-res image`() {
        val pageHtml = """
            <html><head>
              <meta content="Usuario Teste no Instagram: &quot;Foto publica &amp; bonita&#x27;&quot;" property="og:title" />
              <meta property="og:image" content="https://scontent.cdninstagram.com/v/t51.2885-15/12345_n.jpg?stp=c288&amp;oe=AAA" />
              <meta content="image" property="og:type" />
            </head></html>
        """.trimIndent()

        val fullRes = "https://scontent.cdninstagram.com/v/t51.2885-15/12345_n.jpg?oe=FULL"
        val meta = InstagramMetaParser.buildMetadata(
            originalUrl = "https://www.instagram.com/p/DgJgmzgPF0L/",
            shortcode = "DgJgmzgPF0L",
            pageHtml = pageHtml,
            fullResImageUrl = fullRes,
        )

        assertNotNull(meta)
        assertEquals(MediaType.IMAGE, meta?.mediaType)
        assertEquals("Usuario Teste", meta?.uploader)
        assertTrue(meta?.title?.contains("Foto publica & bonita'") == true)
        assertEquals(listOf(fullRes), meta?.imageUrls)
    }

    @Test
    fun `extracts embed carousel images while ignoring avatar thumbnails`() {
        val pageHtml = """
            <meta property="og:title" content="Autor (@autor) on Instagram: &quot;Carrossel&quot;" />
            <meta property="og:image" content="https://scontent.cdninstagram.com/v/slide1_n.jpg" />
        """.trimIndent()
        val embedHtml = """
            <img class="Avatar" src="https://scontent.cdninstagram.com/v/t51.2885-19/s150x150/avatar.jpg" />
            <img class="EmbeddedMediaImage" src="https://scontent.cdninstagram.com/v/slide1_n.jpg?oe=1" />
            <img src="https://scontent.cdninstagram.com/v/slide2_n.jpg?oe=2" class="EmbeddedMediaImage" />
            <script>{"display_url":"https:\/\/scontent.cdninstagram.com\/v\/slide3_n.jpg?oe=3"}</script>
        """.trimIndent()

        val meta = InstagramMetaParser.buildMetadata(
            originalUrl = "https://www.instagram.com/p/CAROUSEL1/",
            shortcode = "CAROUSEL1",
            pageHtml = pageHtml,
            embedHtml = embedHtml,
        )

        assertNotNull(meta)
        assertEquals(MediaType.CAROUSEL, meta?.mediaType)
        assertEquals(3, meta?.playlistItems?.size)
        assertFalse(meta?.imageUrls?.any { it.contains("avatar.jpg") } == true)
    }

    @Test
    fun `extracts direct video stream when og video or embed video_url is present`() {
        val pageHtml = """
            <meta property="og:title" content="Creator on Instagram: &quot;Reel&quot;" />
            <meta property="og:image" content="https://scontent.cdninstagram.com/v/thumb.jpg" />
            <meta property="og:type" content="video" />
        """.trimIndent()
        val embedWithVideo = """
            <script>{"video_url":"https:\/\/video.cdninstagram.com\/v\/clip.mp4?oe=VID"}</script>
        """.trimIndent()

        val videoMeta = InstagramMetaParser.buildMetadata(
            originalUrl = "https://www.instagram.com/p/VID123/",
            shortcode = "VID123",
            pageHtml = pageHtml,
            embedHtml = embedWithVideo,
        )
        assertNotNull(videoMeta)
        assertEquals(MediaType.VIDEO, videoMeta?.mediaType)
        assertEquals("https://video.cdninstagram.com/v/clip.mp4?oe=VID", videoMeta?.videoStreamUrl)
        assertEquals("https://video.cdninstagram.com/v/clip.mp4?oe=VID", videoMeta?.qualities?.first()?.directUrl)

        val videoMissingStream = InstagramMetaParser.buildMetadata(
            originalUrl = "https://www.instagram.com/p/VID123/",
            shortcode = "VID123",
            pageHtml = pageHtml,
            embedHtml = "",
        )
        assertNull(videoMissingStream)
    }

    @Test
    fun `extracts multi-photo carousel from double-escaped contextJSON and ignores hover cards`() {
        val pageHtml = """
            <meta property="og:title" content="Ei Nerd on Instagram: &quot;TEORIA: O PS6&quot;" />
            <meta property="og:image" content="https://instagram.fmvf2-1.fna.fbcdn.net/v/t51/slide1.jpg?stp=c288" />
        """.trimIndent()

        val innerJson = """
            {
              "context": {"type": "GraphSidecar", "shortcode": "J2HGVL"},
              "gql_data": {
                "shortcode_media": {
                  "__typename": "GraphSidecar",
                  "owner": {"username": "einerd.com.br"},
                  "edge_sidecar_to_children": {
                    "edges": [
                      {"node": {"is_video": false, "display_url": "https:\/\/instagram.fmvf2-1.fna.fbcdn.net\/v\/t51\/slide1.jpg?oe=1"}},
                      {"node": {"is_video": false, "display_resources": [
                        {"config_width": 640, "config_height": 800, "src": "https:\/\/instagram.fmvf2-1.fna.fbcdn.net\/v\/t51\/slide2_low.jpg"},
                        {"config_width": 1080, "config_height": 1350, "src": "https:\/\/instagram.fmvf2-1.fna.fbcdn.net\/v\/t51\/slide2_hd.jpg"}
                      ]}},
                      {"node": {"is_video": false, "display_url": "https:\/\/instagram.fmvf2-1.fna.fbcdn.net\/v\/t51\/slide3.jpg?oe=3"}}
                    ]
                  }
                }
              }
            }
        """.trimIndent()
        val encodedContext = Json.encodeToString(innerJson)
        val embedHtml = """
            <div class="HoverCardPhotos"><img src="https://instagram.fmvf2-1.fna.fbcdn.net/v/t51/hover_n.webp" /></div>
            <img class="Avatar" src="https://scontent.cdninstagram.com/v/t51.2885-19/s150x150/avatar.jpg" />
            <img class="EmbeddedMediaImage" src="https://instagram.fmvf2-1.fna.fbcdn.net/v/t51/slide1.jpg?oe=1" />
            <script>["ServerJS",[[{"isRichEmbed":true,"isSidecar":true,"contextJSON":$encodedContext}]]]</script>
        """.trimIndent()

        val meta = InstagramMetaParser.buildMetadata(
            originalUrl = "https://www.instagram.com/p/J2HGVL/?img_index=1",
            shortcode = "J2HGVL",
            pageHtml = pageHtml,
            embedHtml = embedHtml,
        )

        assertNotNull(meta)
        assertEquals(MediaType.CAROUSEL, meta?.mediaType)
        assertTrue(meta?.isPlaylist == true)
        assertEquals(3, meta?.playlistCount)
        assertEquals(
            listOf(
                "https://instagram.fmvf2-1.fna.fbcdn.net/v/t51/slide1.jpg?oe=1",
                "https://instagram.fmvf2-1.fna.fbcdn.net/v/t51/slide2_hd.jpg",
                "https://instagram.fmvf2-1.fna.fbcdn.net/v/t51/slide3.jpg?oe=3",
            ),
            meta?.playlistItems?.map { it.url },
        )
        assertFalse(meta?.imageUrls?.any { it.contains("hover_n") || it.contains("avatar") } == true)
    }

    @Test
    fun `extracts mixed photo and video carousel as CAROUSEL instead of single VIDEO`() {
        val pageHtml = """
            <meta property="og:title" content="PlayStation on Instagram: &quot;Mixed&quot;" />
            <meta property="og:video" content="https://instagram.fmvf2-1.fna.fbcdn.net/v/t16/clip2.mp4?oe=2" />
            <meta property="og:type" content="video" />
        """.trimIndent()

        val innerJson = """
            {
              "gql_data": {
                "shortcode_media": {
                  "__typename": "GraphSidecar",
                  "edge_sidecar_to_children": {
                    "edges": [
                      {"node": {"is_video": false, "display_url": "https:\/\/instagram.fmvf2-1.fna.fbcdn.net\/v\/t51\/slide1.jpg?oe=1"}},
                      {"node": {
                        "is_video": true,
                        "display_url": "https:\/\/instagram.fmvf2-1.fna.fbcdn.net\/v\/t51\/thumb2.jpg?oe=2",
                        "video_url": "https:\/\/instagram.fmvf2-1.fna.fbcdn.net\/v\/t16\/clip2.mp4?oe=2"
                      }},
                      {"node": {"is_video": false, "display_url": "https:\/\/instagram.fmvf2-1.fna.fbcdn.net\/v\/t51\/slide3.jpg?oe=3"}}
                    ]
                  }
                }
              }
            }
        """.trimIndent()
        val encodedContext = Json.encodeToString(innerJson)
        val embedHtml = """<script>{"isSidecar":true,"contextJSON":$encodedContext}</script>"""

        val meta = InstagramMetaParser.buildMetadata(
            originalUrl = "https://www.instagram.com/p/MIXED1/",
            shortcode = "MIXED1",
            pageHtml = pageHtml,
            embedHtml = embedHtml,
        )

        assertNotNull(meta)
        assertEquals(MediaType.CAROUSEL, meta?.mediaType)
        assertTrue(meta?.isPlaylist == true)
        assertEquals(3, meta?.playlistCount)
        assertEquals(MediaType.IMAGE, meta?.playlistItems?.get(0)?.itemType)
        assertEquals(MediaType.VIDEO, meta?.playlistItems?.get(1)?.itemType)
        assertEquals(
            "https://instagram.fmvf2-1.fna.fbcdn.net/v/t16/clip2.mp4?oe=2",
            meta?.playlistItems?.get(1)?.url,
        )
        assertEquals(
            "https://instagram.fmvf2-1.fna.fbcdn.net/v/t51/thumb2.jpg?oe=2",
            meta?.playlistItems?.get(1)?.thumbnailUrl,
        )
        assertEquals(MediaType.IMAGE, meta?.playlistItems?.get(2)?.itemType)
        assertEquals(2, meta?.imageUrls?.size)
    }

    @Test
    fun `falls back to contextJSON owner username and caption when OG tags are missing`() {
        val innerJson = """
            {
              "gql_data": {
                "shortcode_media": {
                  "__typename": "GraphSidecar",
                  "owner": {"username": "einerd.com.br"},
                  "edge_media_to_caption": {
                    "edges": [{"node": {"text": "TEORIA: O PS6 aparece"}}]
                  },
                  "edge_sidecar_to_children": {
                    "edges": [
                      {"node": {"is_video": false, "display_url": "https:\/\/scontent.cdninstagram.com\/v\/p1.jpg"}},
                      {"node": {"is_video": false, "display_url": "https:\/\/scontent.cdninstagram.com\/v\/p2.jpg"}}
                    ]
                  }
                }
              }
            }
        """.trimIndent()
        val encodedContext = Json.encodeToString(innerJson)
        val embedHtml = """<script>{"contextJSON":null}{"contextJSON":$encodedContext}</script>"""

        val meta = InstagramMetaParser.buildMetadata(
            originalUrl = "https://www.instagram.com/p/J2HGVL/",
            shortcode = "J2HGVL",
            pageHtml = "",
            embedHtml = embedHtml,
        )

        assertNotNull(meta)
        assertEquals("einerd.com.br", meta?.uploader)
        assertEquals("TEORIA: O PS6 aparece", meta?.title)
        assertEquals(MediaType.CAROUSEL, meta?.mediaType)
        assertEquals(2, meta?.playlistCount)
    }

    @Test
    fun `extracts shortcodes from standard and username-prefixed Instagram URLs`() {
        assertEquals(
            "J2HGVL",
            InstagramMetaParser.extractShortcode("https://www.instagram.com/p/J2HGVL/?jplx=d3dmaDc4OW55ZTR1"),
        )
        assertEquals(
            "ABC_123-xy",
            InstagramMetaParser.extractShortcode("https://www.instagram.com/einerd.com.br/p/ABC_123-xy/?img_index=2"),
        )
        assertEquals("Chunk8-jurw", InstagramMetaParser.extractShortcode("https://www.instagram.com/reel/Chunk8-jurw/"))
        assertEquals("XYZ789", InstagramMetaParser.extractShortcode("https://instagr.am/p/XYZ789/"))
    }
}
