package de.demogorgi.sds011native.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.app.Activity
import android.widget.Toast
import androidx.core.content.FileProvider
import de.demogorgi.sds011native.R
import java.io.File

object FileSharing {

    fun shareFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.share_file_missing, file.name), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = when {
                file.name.endsWith(".csv", ignoreCase = true) -> "text/csv"
                file.name.endsWith(".kml", ignoreCase = true) -> "application/vnd.google-earth.kml+xml"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            launchChooser(context, intent, context.getString(R.string.share_file_chooser, file.name))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.share_error, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
    }

    fun shareFiles(context: Context, files: List<File>) {
        val validFiles = files.filter { it.exists() }
        if (validFiles.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.share_none), Toast.LENGTH_SHORT).show()
            return
        }

        if (validFiles.size == 1) {
            shareFile(context, validFiles.first())
            return
        }

        try {
            val uris = ArrayList(validFiles.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            })

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            launchChooser(context, intent, context.getString(R.string.share_files_count, validFiles.size))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.share_error, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
    }

    private fun launchChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title)
        if (!context.hasActivity()) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun Context.hasActivity(): Boolean = when (this) {
        is Activity -> true
        is ContextWrapper -> baseContext.hasActivity()
        else -> false
    }
}
