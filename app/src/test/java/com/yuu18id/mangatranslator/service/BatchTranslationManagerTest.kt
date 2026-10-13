package com.yuu18id.mangatranslator.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchTranslationManagerTest {

    @Test
    fun testNaturalOrderComparatorSortsNumberedPagesCorrectly() {
        val comparator = BatchTranslationManager.Companion.NaturalOrderComparator<String> { it }
        val pages = listOf(
            "page_10.png",
            "page_1.png",
            "page_2.png",
            "page_20.png",
            "page_100.png",
            "page_3.png"
        )
        val sorted = pages.sortedWith(comparator)
        val expected = listOf(
            "page_1.png",
            "page_2.png",
            "page_3.png",
            "page_10.png",
            "page_20.png",
            "page_100.png"
        )
        assertEquals("Natural ordering must sort numeric page suffixes in numerical order", expected, sorted)
    }

    @Test
    fun testNaturalOrderComparatorHandlesChapterAndPagePrefixes() {
        val comparator = BatchTranslationManager.Companion.NaturalOrderComparator<String> { it }
        val pages = listOf(
            "ch2_p1.jpg",
            "ch1_p10.jpg",
            "ch1_p2.jpg",
            "ch1_p1.jpg",
            "ch2_p10.jpg"
        )
        val sorted = pages.sortedWith(comparator)
        val expected = listOf(
            "ch1_p1.jpg",
            "ch1_p2.jpg",
            "ch1_p10.jpg",
            "ch2_p1.jpg",
            "ch2_p10.jpg"
        )
        assertEquals(expected, sorted)
    }

    @Test
    fun testNaturalOrderComparatorIsCaseInsensitive() {
        val comparator = BatchTranslationManager.Companion.NaturalOrderComparator<String> { it }
        val pages = listOf("page_2.png", "Page_1.png", "PAGE_10.png")
        val sorted = pages.sortedWith(comparator)
        assertEquals(listOf("Page_1.png", "page_2.png", "PAGE_10.png"), sorted)
    }

    @Test
    fun testComputeBatchSuccessWhenAllPagesComplete() {
        val isSuccess = BatchTranslationManager.Companion.computeBatchSuccess(
            finalCompleted = 10,
            finalFailed = 0,
            total = 10,
            isCancelled = false
        )
        assertTrue("Batch with all 10 pages completed and 0 failures must succeed", isSuccess)
    }

    @Test
    fun testComputeBatchSuccessReturnsFalseWhenCancelledWithPartialProgress() {
        // Finding REV-01 regression test:
        // A cancelled batch with 2 completed pages and 0 failed pages must NOT be marked successful
        val isSuccess = BatchTranslationManager.Companion.computeBatchSuccess(
            finalCompleted = 2,
            finalFailed = 0,
            total = 10,
            isCancelled = true
        )
        org.junit.Assert.assertFalse("Cancelled batch must not be marked successful even if partial pages succeeded", isSuccess)
    }

    @Test
    fun testComputeBatchSuccessReturnsFalseWhenPagesFailed() {
        val isSuccess = BatchTranslationManager.Companion.computeBatchSuccess(
            finalCompleted = 8,
            finalFailed = 2,
            total = 10,
            isCancelled = false
        )
        org.junit.Assert.assertFalse("Batch with failed pages must not be marked successful", isSuccess)
    }

    @Test
    fun testComputeBatchSuccessReturnsFalseWhenIncomplete() {
        val isSuccess = BatchTranslationManager.Companion.computeBatchSuccess(
            finalCompleted = 5,
            finalFailed = 0,
            total = 10,
            isCancelled = false
        )
        org.junit.Assert.assertFalse("Incomplete batch (5/10) must not be marked successful", isSuccess)
    }

    @Test
    fun testComputeBatchSuccessSinglePageRetrySuccessAndCancellation() {
        val success = BatchTranslationManager.Companion.computeBatchSuccess(
            finalCompleted = 1,
            finalFailed = 0,
            total = 10,
            isCancelled = false,
            singlePageIndex = 3,
            singlePageStatus = com.yuu18id.mangatranslator.domain.model.BatchPageStatus.COMPLETED
        )
        assertTrue("Single page retry that completed must be marked successful", success)

        val cancelled = BatchTranslationManager.Companion.computeBatchSuccess(
            finalCompleted = 0,
            finalFailed = 0,
            total = 10,
            isCancelled = true,
            singlePageIndex = 3,
            singlePageStatus = com.yuu18id.mangatranslator.domain.model.BatchPageStatus.CANCELLED
        )
        org.junit.Assert.assertFalse("Cancelled single page retry must not be marked successful", cancelled)
    }
}
