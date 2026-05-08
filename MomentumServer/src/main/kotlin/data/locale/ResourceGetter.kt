package com.example.data.locale

import java.text.MessageFormat
import java.util.*

object ResourceGetter {
    private const val BASE_NAME = "pushmessages"
    private val DEFAULT_LOCALE: Locale = Locale.forLanguageTag("ru")

    private fun bundle(locale: Locale): ResourceBundle {
        return ResourceBundle.getBundle(BASE_NAME, locale)
    }


    fun t(key: String, locale: Locale = DEFAULT_LOCALE): String =
        bundle(locale).getString(key)

    fun tf(key: String, vararg args: Any?, locale: Locale = DEFAULT_LOCALE): String {
        val pattern = bundle(locale).getString(key)

        val safeArgs = args.map { arg ->
            when (arg) {
                is Number -> arg.toString()
                else -> arg
            }
        }.toTypedArray()

        return MessageFormat(pattern, locale).format(safeArgs)
    }
}
