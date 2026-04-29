package com.example.data.locale

import java.text.MessageFormat
import java.util.*

object ResourceGetter {
    private const val BASE_NAME = "pushmessages"

    private fun bundle(locale: Locale): ResourceBundle {
        val b = ResourceBundle.getBundle(BASE_NAME, locale)
        return b
    }


    fun t(key: String, locale: Locale = Locale("ru")): String =
        bundle(locale).getString(key)

    fun tf(key: String, vararg args: Any?, locale: Locale = Locale("ru")): String {
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
