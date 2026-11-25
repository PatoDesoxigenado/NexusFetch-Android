package io.github.patodesoxigenado.nexusfetch.ui.navigation

import android.net.Uri

data class DeepLinkPayload(
    val id: Long = System.nanoTime(),
    val targetRoute: String,
    val url: String? = null,
    val uris: List<Uri> = emptyList(),
)
