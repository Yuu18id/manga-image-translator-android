package com.yuu18id.mangatranslator.data.translation.prompt

import com.yuu18id.mangatranslator.domain.model.TextBlock

/**
 * Robust LLM translation response parser.
 *
 * Handles both cloud and local model outputs (e.g., llama.cpp / Ollama / LM Studio),
 * accommodating contiguous single-line strings, markdown code blocks, thinking tags,
 * and diverse numbering conventions (1:, 1., [1], etc.).
 */
object LlmResponseParser {

    private val THINK_TAG_REGEX = Regex("""(?s)<(?:think|thought)>.*?</(?:think|thought)>""")
    private val CODE_BLOCK_FENCE_REGEX = Regex("""(?m)^```(?:json|markdown|text)?\s*$|^```\s*$""")

    // Matches item markers using positive lookbehind so preceding spaces/brackets aren't consumed:
    // 1. <|1|> or <1> or <|1|>:
    // 2. [1] or [1]: or [1].
    // 3. (1) or (1): or (1).
    // 4. 1: or 1. or 1 - or 1)
    private val MARKER_REGEX = Regex(
        """(?<=\A|[\r\n\s/|\]\)"'”’])(?:<\|?(\d{1,3})\|?>[:.\-\s]*|\[(\d{1,3})\][:.\-\s]*|\((\d{1,3})\)[:.\-\s]*|(\d{1,3})[\s]*[:.\-)][\s.:\-]*)"""
    )

    private val PROMPT_LEAKAGE_KEYWORDS = listOf(
        "NEVER MERGE",
        "MERGE ADJACENT",
        "SPATIAL COORDINATES",
        "SPEECH BUBBLE",
        "SPEECH BUBBLES",
        "PHYSICALLY SEPARATE",
        "STRICT 1:1",
        "CRITICAL:",
        "CRITICAL OUTPUT FORMAT",
        "TRANSLATE THE FOLLOWING",
        "TRANSLATE LINE BY LINE",
        "KEEP THE EXACT PREFIX",
        "KEEP THE PREFIX",
        "OUTPUT EXACTLY",
        "DO NOT OUTPUT",
        "DO NOT MERGE",
        "EXPERT MANGA",
        "COMIC DIALOGUE TRANSLATOR"
    )

    /**
     * Detects if the model regurgitated / echoed prompt instructions instead of translating.
     */
    fun isPromptLeakage(text: String): Boolean {
        if (text.isBlank()) return false
        val upper = text.uppercase()
        return PROMPT_LEAKAGE_KEYWORDS.any { upper.contains(it) }
    }

    data class MarkerMatch(
        val number: Int,
        val startIndex: Int,
        val endIndex: Int
    )

    /**
     * Parses the raw LLM content and maps translations to corresponding TextBlock indices (0-based).
     *
     * @param content Raw string content returned from LLM choice.
     * @param targetCount Expected number of text blocks to translate.
     * @return Map of block index (0-based) to translated text string.
     */
    fun parse(content: String, targetCount: Int): Map<Int, String> {
        if (content.isBlank()) return emptyMap()

        // 1. Clean reasoning / think tags and markdown fences
        var cleaned = THINK_TAG_REGEX.replace(content, "")
        cleaned = CODE_BLOCK_FENCE_REGEX.replace(cleaned, "").trim()

        if (cleaned.isBlank()) return emptyMap()

        val results = mutableMapOf<Int, String>()

        // 2. Find all potential index markers
        val rawMatches = MARKER_REGEX.findAll(cleaned).mapNotNull { match ->
            val numStr = match.groupValues[1].ifEmpty {
                match.groupValues[2].ifEmpty {
                    match.groupValues[3].ifEmpty {
                        match.groupValues[4]
                    }
                }
            }
            val num = numStr.toIntOrNull() ?: return@mapNotNull null
            MarkerMatch(
                number = num,
                startIndex = match.range.first,
                endIndex = match.range.last + 1
            )
        }.toList()

        // 3. Filter markers to find a valid monotonic sequence (1, 2, 3, ...)
        val validMarkers = filterMonotonicMarkers(rawMatches, targetCount)

        if (validMarkers.isNotEmpty()) {
            for (i in validMarkers.indices) {
                val current = validMarkers[i]
                val targetIndex = current.number - 1

                val startPos = current.endIndex
                val endPos = if (i + 1 < validMarkers.size) {
                    validMarkers[i + 1].startIndex
                } else {
                    cleaned.length
                }

                if (startPos <= endPos && startPos <= cleaned.length) {
                    val rawSlice = cleaned.substring(startPos, minOf(endPos, cleaned.length))
                    val cleanedSlice = cleanDialogueText(rawSlice)
                    if (cleanedSlice.isNotBlank() && !isPromptLeakage(cleanedSlice)) {
                        results[targetIndex] = cleanedSlice
                    }
                }
            }
        }

        // 4. Fallback strategies if marker extraction didn't populate blocks
        if (results.isEmpty()) {
            if (targetCount == 1) {
                val single = cleanDialogueText(cleaned)
                if (single.isNotBlank() && !isPromptLeakage(single)) {
                    results[0] = single
                }
            } else {
                val nonBlankLines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (nonBlankLines.size == targetCount) {
                    nonBlankLines.forEachIndexed { idx, line ->
                        val cleanedLine = cleanDialogueText(line)
                        if (cleanedLine.isNotBlank() && !isPromptLeakage(cleanedLine)) {
                            results[idx] = cleanedLine
                        }
                    }
                }
            }
        }

        return results
    }


    /**
     * Filters matches into a plausible increasing sequence starting near 1
     * to avoid false positives from dates or arbitrary numbers inside dialogue.
     */
    private fun filterMonotonicMarkers(matches: List<MarkerMatch>, targetCount: Int): List<MarkerMatch> {
        if (matches.isEmpty()) return emptyList()

        val filtered = mutableListOf<MarkerMatch>()
        var lastNum = 0

        for (match in matches) {
            val num = match.number
            // If targetCount is known, ignore numbers excessively large
            if (targetCount > 0 && num > targetCount + 5) continue

            if (filtered.isEmpty()) {
                // First marker should ideally be 1, but allow 0 or 2 if model started slightly off
                if (num in 0..2) {
                    filtered.add(match)
                    lastNum = num
                }
            } else {
                // Must be strictly increasing and not jumping wildly
                if (num > lastNum && num <= lastNum + 3) {
                    filtered.add(match)
                    lastNum = num
                }
            }
        }

        // If the filtered list captured at least 2 markers or 1 when targetCount == 1
        if (filtered.isNotEmpty() && (filtered.size >= 2 || targetCount == 1 || matches.size == 1)) {
            return filtered
        }

        return matches
    }

    /**
     * Cleans a dialogue slice:
     * - Strips redundant delimiter patterns like "Text / [Text]"
     * - Strips outer brackets `[...]`, quotes, slashes, dashes
     */
    fun cleanDialogueText(text: String): String {
        var s = text.trim()

        // Strip leading/trailing delimiters, blockquotes (>), brackets, and tag fragments
        s = s.trimStart('/', '|', '-', ':', '>', ' ', '\t', '\r', '\n')
        s = s.trimEnd('/', '|', '-', '<', ' ', '\t', '\r', '\n')

        // Remove tag artifacts like <|1|> or </|1|> if present inside slice
        s = s.replace(Regex("""</?\|?\d+\|?>"""), "").trim()

        // Handle models outputting "Text / [Text]" or "[Text] / Text"
        if (s.contains(" / [") && s.endsWith("]")) {
            val bracketPart = s.substringAfter(" / [").substringBeforeLast("]").trim()
            if (bracketPart.isNotBlank()) {
                s = bracketPart
            }
        } else if (s.startsWith("[") && s.contains("] / ")) {
            val bracketPart = s.substringAfter("[").substringBefore("] / ").trim()
            if (bracketPart.isNotBlank()) {
                s = bracketPart
            }
        }

        // Strip enclosing square brackets `[ ... ]`
        if (s.startsWith('[') && s.endsWith(']') && s.length >= 2) {
            s = s.substring(1, s.length - 1).trim()
        }

        // Strip enclosing double quotes `" ... "` or `“ ... ”`
        if ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith('“') && s.endsWith('”'))) {
            if (s.length >= 2) {
                s = s.substring(1, s.length - 1).trim()
            }
        }

        return s.trim()
    }

    /**
     * Applies parsed translations to the provided list of TextBlocks.
     */
    fun applyToBlocks(content: String, textBlocks: List<TextBlock>): List<TextBlock> {
        if (textBlocks.isEmpty()) return emptyList()

        val parsedMap = parse(content, textBlocks.size)
        val resultBlocks = textBlocks.map { it.copy() }.toMutableList()

        for ((index, text) in parsedMap) {
            if (index in resultBlocks.indices && text.isNotBlank()) {
                resultBlocks[index] = resultBlocks[index].copy(translatedText = text)
            }
        }

        return resultBlocks
    }
}
