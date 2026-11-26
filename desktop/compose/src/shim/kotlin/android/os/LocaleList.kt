package android.os

import java.util.Locale

class LocaleList(private vararg val list: Locale) {
    operator fun get(i: Int): Locale = list[i]
    fun size() = list.size
    val isEmpty: Boolean get() = list.isEmpty()
    fun toLanguageTags(): String = list.joinToString(",") { it.toLanguageTag() }
    companion object { @JvmStatic fun getDefault() = LocaleList(Locale.getDefault()); @JvmStatic fun getAdjustedDefault() = getDefault() }
}
