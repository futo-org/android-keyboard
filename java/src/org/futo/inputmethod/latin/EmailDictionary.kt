package org.futo.inputmethod.latin

import android.content.Context
import java.util.Locale

class EmailDictionary(context: Context) :
    ExpandableBinaryDictionary(context, "email", Locale.ENGLISH, TYPE_EMAIL, null)
{
    private val emailDomains = listOf(
        "gmail.com",  "yahoo.com",  "outlook.com", "hotmail.com",
        "icloud.com",
        "proton.me", "protonmail.com", "pm.me", "protonmail.ch",
        "fastmail.com",
        "tuta.com", "tutamail.com", "tuta.io", "tutanota.com", "tutanota.de",
    )
    override fun loadInitialContentsLocked() {
        emailDomains.reversed().forEachIndexed { i, it ->
            addEntry(NgramContext.EMAIL_DOMAIN, i, it, 1)
        }
    }

    fun addEntry(ngramContext: NgramContext, frequency: Int, entry: String, timestamp: Int = (System.currentTimeMillis() / 1000).toInt()) {
        if(ngramContext != NgramContext.EMAIL_DOMAIN) addUnigramEntry(entry, frequency, null, 0, false, false, timestamp)
        addNgramEntry(ngramContext, entry, frequency, timestamp)
        updateEntriesForWord(ngramContext, entry, true, frequency, timestamp)
    }

    fun addEntryNow(ngramContext: NgramContext, frequency: Int, entry: String) = addEntry(ngramContext, frequency, entry)
}