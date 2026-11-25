package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitterMediaFilterTest {

    @Test
    fun `ignores quoted tweet media and keeps only root tweet images upgraded to orig`() {
        val payload = """
            {
              "text": "Tweet principal com foto",
              "user": { "name": "Dev", "screen_name": "dev_br" },
              "photos": [
                { "url": "https://pbs.twimg.com/media/ROOT_IMG.jpg?name=medium", "width": 1200, "height": 800 }
              ],
              "mediaDetails": [
                { "type": "photo", "media_url_https": "https://pbs.twimg.com/media/ROOT_IMG.jpg?name=medium" }
              ],
              "quoted_tweet": {
                "text": "Tweet citado ignorado",
                "photos": [
                  { "url": "https://pbs.twimg.com/media/QUOTED_IMG_LEAK.jpg" }
                ],
                "mediaDetails": [
                  { "type": "video", "media_url_https": "https://pbs.twimg.com/ext_tw_video_thumb/999.jpg" }
                ]
              }
            }
        """.trimIndent()

        val metadata = TwitterMediaFilter.parseSyndicationPayload("https://x.com/dev_br/status/123", payload)
        assertNotNull(metadata)
        assertEquals(MediaType.IMAGE, metadata?.mediaType)
        assertEquals(1, metadata?.imageUrls?.size)
        assertTrue(metadata?.imageUrls?.first()?.contains("ROOT_IMG.jpg") == true)
        assertTrue(metadata?.imageUrls?.first()?.contains("name=orig") == true)
        assertFalse(metadata?.imageUrls?.any { it.contains("QUOTED_IMG_LEAK") } == true)
    }

    @Test
    fun `parses animated gif and multi-photo carousel properly`() {
        val gifPayload = """
            {
              "text": "Gif animado",
              "user": { "name": "Alice", "screen_name": "alice" },
              "mediaDetails": [
                {
                  "type": "animated_gif",
                  "media_url_https": "https://pbs.twimg.com/tweet_video_thumb/abc.jpg",
                  "video_info": {
                    "variants": [
                      { "bitrate": 0, "content_type": "video/mp4", "url": "https://video.twimg.com/tweet_video/abc.mp4" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val gifMeta = TwitterMediaFilter.parseSyndicationPayload("https://x.com/alice/status/456", gifPayload)
        assertEquals(MediaType.GIF, gifMeta?.mediaType)
        assertEquals("https://video.twimg.com/tweet_video/abc.mp4", gifMeta?.videoStreamUrl)
        assertEquals("https://video.twimg.com/tweet_video/abc.mp4", gifMeta?.qualities?.first()?.directUrl)
        assertTrue(TwitterMediaFilter.requiresSingleItemFilter("https://x.com/alice/status/456"))
    }

    @Test
    fun `extracts video variants with per-resolution directUrl and supports FxTwitter fallback`() {
        val syndicationVideo = """
            {
              "text": "Clipe em HD",
              "user": { "name": "Anime", "screen_name": "jujutsu" },
              "mediaDetails": [
                {
                  "type": "video",
                  "media_url_https": "https://pbs.twimg.com/ext_tw_video_thumb/100.jpg",
                  "video_info": {
                    "duration_millis": 4033,
                    "variants": [
                      {
                        "bitrate": 832000,
                        "content_type": "video/mp4",
                        "url": "https://video.twimg.com/amplify_video/100/vid/avc1/848x360/low.mp4"
                      },
                      {
                        "bitrate": 2176000,
                        "content_type": "video/mp4",
                        "url": "https://video.twimg.com/amplify_video/100/vid/avc1/1280x544/high.mp4"
                      },
                      {
                        "content_type": "application/x-mpegURL",
                        "url": "https://video.twimg.com/amplify_video/100/pl/playlist.m3u8"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val meta = TwitterMediaFilter.parseSyndicationPayload(
            "https://x.com/jujutsu/status/2125187644954939520",
            syndicationVideo,
        )
        assertNotNull(meta)
        assertEquals(MediaType.VIDEO, meta?.mediaType)
        assertEquals(4, meta?.durationSec)
        assertEquals(
            "https://video.twimg.com/amplify_video/100/vid/avc1/1280x544/high.mp4",
            meta?.videoStreamUrl,
        )
        val hdOpt = meta?.qualities?.firstOrNull { it.height == 544 }
        assertEquals(
            "https://video.twimg.com/amplify_video/100/vid/avc1/1280x544/high.mp4",
            hdOpt?.directUrl,
        )
        val sdOpt = meta?.qualities?.firstOrNull { it.height == 360 }
        assertEquals(
            "https://video.twimg.com/amplify_video/100/vid/avc1/848x360/low.mp4",
            sdOpt?.directUrl,
        )
    }

    @Test
    fun `parses FxTwitter fallback video payload with directUrl variants`() {
        val fxPayload = """
            {
              "code": 200,
              "tweet": {
                "text": "Fallback FxTwitter",
                "author": { "name": "User", "screen_name": "fx_user" },
                "media": {
                  "videos": [
                    {
                      "url": "https://video.twimg.com/fx/720p.mp4",
                      "thumbnail_url": "https://pbs.twimg.com/fx/thumb.jpg",
                      "duration": 12.4,
                      "type": "video",
                      "formats": [
                        {
                          "url": "https://video.twimg.com/amplify_video/9/vid/avc1/1280x720/hd.mp4",
                          "bitrate": 2500000,
                          "container": "mp4"
                        },
                        {
                          "url": "https://video.twimg.com/amplify_video/9/vid/avc1/640x360/sd.mp4",
                          "bitrate": 650000,
                          "container": "mp4"
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val fxMeta = TwitterMediaFilter.parseFxTwitterPayload("https://x.com/fx_user/status/99", fxPayload)
        assertNotNull(fxMeta)
        assertEquals(MediaType.VIDEO, fxMeta?.mediaType)
        assertEquals(12, fxMeta?.durationSec)
        assertEquals(
            "https://video.twimg.com/amplify_video/9/vid/avc1/1280x720/hd.mp4",
            fxMeta?.videoStreamUrl,
        )
        assertEquals(
            "https://video.twimg.com/amplify_video/9/vid/avc1/1280x720/hd.mp4",
            fxMeta?.qualities?.first()?.directUrl,
        )
    }
}
