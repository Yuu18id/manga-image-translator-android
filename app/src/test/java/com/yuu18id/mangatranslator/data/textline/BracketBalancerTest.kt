package com.yuu18id.mangatranslator.data.textline

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BracketBalancerTest {

    private lateinit var balancer: BracketBalancer

    @Before
    fun setUp() {
        balancer = BracketBalancer()
    }

    @Test
    fun testAlreadyBalancedTextUntouched() {
        val input = "「こんにちは」"
        assertEquals("「こんにちは」", balancer.balance(input))
    }

    @Test
    fun testUnclosedJapaneseQuoteIsClosed() {
        val input = "「こんにちは"
        assertEquals("「こんにちは」", balancer.balance(input))
    }

    @Test
    fun testUnmatchedCloseBracketIsIgnored() {
        val input = "こんにちは」"
        assertEquals("こんにちは", balancer.balance(input))
    }

    @Test
    fun testNestedBracketsBalanced() {
        val input = "「『テスト』」"
        assertEquals("「『テスト』」", balancer.balance(input))
    }

    @Test
    fun testNestedUnclosedBracketsClosedInReverseOrder() {
        val input = "「『テスト"
        assertEquals("「『テスト』」", balancer.balance(input))
    }

    @Test
    fun testAsciiParenthesesBalanced() {
        val input = "(Note: Translation note"
        assertEquals("(Note: Translation note)", balancer.balance(input))
    }

    @Test
    fun testPlainPlainTextUntouched() {
        val input = "Just standard dialogue with no brackets."
        assertEquals("Just standard dialogue with no brackets.", balancer.balance(input))
    }
}
