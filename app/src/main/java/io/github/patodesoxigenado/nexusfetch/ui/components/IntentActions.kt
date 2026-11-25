package io.github.patodesoxigenado.nexusfetch.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.patodesoxigenado.nexusfetch.domain.model.PublishedMedia

/**
 * Safe Android Intent helpers for opening and sharing saved MediaStore files.
 */
object IntentActions {

    fun readClipboardText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
    }

    fun openMedia(context: Context, uriString: String, mimeType: String) {
        val uri = Uri.parse(uriString)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Abrir arquivo com..."))
        }.onFailure {
            Toast.makeText(context, "Nenhum aplicativo encontrado para abrir este arquivo.", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareOutputs(context: Context, outputs: List<PublishedMedia>) {
        if (outputs.isEmpty()) return
        if (outputs.size == 1) {
            shareSingle(context, outputs.first().uriString, outputs.first().mimeType)
            return
        }
        val uris = ArrayList(outputs.map { Uri.parse(it.uriString) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "NexusFetch", uris.first())
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Compartilhar arquivos..."))
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir o compartilhamento.", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareSingle(context: Context, uriString: String, mimeType: String) {
        val uri = Uri.parse(uriString)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Compartilhar para..."))
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir o compartilhamento.", Toast.LENGTH_SHORT).show()
        }
    }
}
