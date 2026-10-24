package de.demogorgi.sds011native.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import de.demogorgi.sds011native.settings.AppSettings
import java.util.Locale

object LocaleHelper {

    fun getLocale(languageMode: Int): Locale {
        return when (languageMode) {
            AppSettings.LANG_DE -> Locale.GERMAN
            AppSettings.LANG_EN -> Locale.ENGLISH
            else -> systemLocale()
        }
    }

    @Suppress("DEPRECATION")
    private fun systemLocale(): Locale =
        Resources.getSystem().configuration.locale

    fun updateContextLocale(context: Context, languageMode: Int): Context {
        val targetLocale = getLocale(languageMode)
        Locale.setDefault(targetLocale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)

        return context.createConfigurationContext(config)
    }
}

@Composable
fun LocalizedContent(
    languageMode: Int,
    content: @Composable () -> Unit
) {
    val currentContext = LocalContext.current
    val localizedContext = remember(languageMode) {
        LocaleHelper.updateContextLocale(currentContext, languageMode)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        content = content
    )
}
