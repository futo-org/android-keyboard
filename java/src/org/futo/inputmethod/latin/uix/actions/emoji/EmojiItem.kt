package org.futo.inputmethod.latin.uix.actions.emoji

data class EmojiItem(
    val emoji: String,
    val description: String,
    val category: String,
    val skinTones: Boolean,
    val tags: List<String>,
    val aliases: List<String>
)