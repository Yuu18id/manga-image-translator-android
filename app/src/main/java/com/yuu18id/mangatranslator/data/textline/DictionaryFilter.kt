package com.yuu18id.mangatranslator.data.textline

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryFilter @Inject constructor() {

    fun applyRules(text: String, rules: Map<String, String>): String {
        var processedText = text
        for ((pattern, replacement) in rules) {
            val regex = Regex(pattern)
            processedText = regex.replace(processedText, replacement)
        }
        return processedText
    }
}
