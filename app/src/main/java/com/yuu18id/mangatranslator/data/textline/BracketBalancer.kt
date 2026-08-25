package com.yuu18id.mangatranslator.data.textline

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BracketBalancer @Inject constructor() {

    fun balance(text: String): String {
        val openBrackets = listOf('「', '『', '【', '（', '(', '[', '{', '《', '〈')
        val closeBrackets = listOf('」', '』', '】', '）', ')', ']', '}', '》', '〉')
        
        val bracketPairs = openBrackets.zip(closeBrackets).toMap()
        val stack = mutableListOf<Char>()
        val result = StringBuilder()
        
        for (char in text) {
            when {
                char in openBrackets -> {
                    stack.add(char)
                    result.append(char)
                }
                char in closeBrackets -> {
                    val matchingOpen = bracketPairs.entries.firstOrNull { it.value == char }?.key
                    if (stack.isNotEmpty() && stack.last() == matchingOpen) {
                        stack.removeLast()
                        result.append(char)
                    } else if (stack.isEmpty()) {
                        // Ignore unmatched close bracket
                    } else {
                        // Mismatched bracket, append without popping
                        result.append(char)
                    }
                }
                else -> {
                    result.append(char)
                }
            }
        }
        
        for (openBracket in stack.reversed()) {
            val closeBracket = bracketPairs[openBracket]
            if (closeBracket != null) {
                result.append(closeBracket)
            }
        }
        
        return result.toString()
    }
}
