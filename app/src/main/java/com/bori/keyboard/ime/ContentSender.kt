package com.bori.keyboard.ime

import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

/**
 * Sends pictures and GIFs into the app being typed in (the "commit content" API that
 * messaging apps support). Files are shared through Bori's FileProvider, read-only, only
 * with the app that receives them.
 */
object ContentSender {
    const val AUTHORITY = "com.bori.keyboard.files"

    /** Whether the field says it takes [mime] (e.g. "image/gif"). */
    fun accepts(info: EditorInfo?, mime: String): Boolean {
        info ?: return false
        return EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(mime, it) }
    }

    /** Returns false when the field doesn't take [mime] or the app refused it. */
    fun send(
        context: Context,
        ic: InputConnection?,
        info: EditorInfo?,
        file: File,
        mime: String,
        label: String,
        link: Uri? = null,
    ): Boolean {
        if (ic == null || info == null || !accepts(info, mime)) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
            val content = InputContentInfoCompat(uri, ClipDescription(label, arrayOf(mime)), link)
            val flags = if (Build.VERSION.SDK_INT >= 25) InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION else 0
            InputConnectionCompat.commitContent(ic, info, content, flags, null)
        }.getOrElse {
            DiagLog.error("send content", it)
            false
        }
    }
}
