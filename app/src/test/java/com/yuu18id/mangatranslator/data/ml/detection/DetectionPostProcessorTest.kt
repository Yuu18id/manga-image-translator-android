package com.yuu18id.mangatranslator.data.ml.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.Point

class DetectionPostProcessorTest {

    @Test
    fun testExpandQuadrilateralBoundaryClamping() {
        val originalPoints = listOf(
            Point(-5.0, -10.0),
            Point(505.0, -10.0),
            Point(505.0, 510.0),
            Point(-5.0, 510.0)
        )

        val clamped = DetectionPostProcessor.expandQuadrilateral(
            points = originalPoints,
            imageWidth = 500,
            imageHeight = 500
        )

        assertEquals(4, clamped.size)
        for (pt in clamped) {
            assertTrue(pt.x >= 0.0)
            assertTrue(pt.y >= 0.0)
            assertTrue(pt.x <= 499.0)
            assertTrue(pt.y <= 499.0)
        }
    }
}
