package com.yuu18id.mangatranslator.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaExporterTest {

    @Test
    fun testSanitizeFolderNameRemovesIllegalCharacters() {
        val folder = "One Piece: Chapter 1/Vol*2?\"<>|"
        val sanitized = MediaExporter.sanitizeFolderName(folder)
        assertEquals("One Piece_ Chapter 1_Vol_2_____", sanitized)
    }

    @Test
    fun testSanitizeFileNamePreservesValidExtension() {
        val name = "page_001.webp"
        val sanitized = MediaExporter.sanitizeFileName(name)
        assertEquals("page_001.webp", sanitized)

        val pngName = "page_002.png"
        val sanitizedPng = MediaExporter.sanitizeFileName(pngName)
        assertEquals("page_002.png", sanitizedPng)
    }

    @Test
    fun testSanitizeFileNameAppendsSourceExtensionWhenMissing() {
        val name = "chapter_page_01"
        val sanitizedWebp = MediaExporter.sanitizeFileName(name, sourceFilePath = "/data/cache/temp.webp")
        assertEquals("chapter_page_01.webp", sanitizedWebp)

        val sanitizedPng = MediaExporter.sanitizeFileName(name, sourceFilePath = "/data/cache/temp.png")
        assertEquals("chapter_page_01.png", sanitizedPng)
    }

    @Test
    fun testSanitizeFileNameReplacesIllegalCharacters() {
        val illegal = "image:part?1*2.png"
        val sanitized = MediaExporter.sanitizeFileName(illegal)
        assertEquals("image_part_1_2.png", sanitized)
    }

    @Test
    fun testSanitizeFileNameGeneratesFallbackForBlankOrDot() {
        val fallbackBlank = MediaExporter.sanitizeFileName("")
        assertTrue(fallbackBlank.startsWith("translated_") && fallbackBlank.endsWith(".png"))

        val fallbackDot = MediaExporter.sanitizeFileName(".hidden")
        assertTrue(fallbackDot.startsWith("translated_") && fallbackDot.endsWith(".png"))
    }
}
