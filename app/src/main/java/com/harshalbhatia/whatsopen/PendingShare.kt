package com.harshalbhatia.whatsopen

object PendingShare {
    @Volatile
    var text: String? = null

    fun consume(): String? {
        val value = text
        text = null
        return value
    }
}
