package com.yuu18id.mangatranslator.data.textline

import com.yuu18id.mangatranslator.domain.model.TextBlock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ReadingOrderSorter @Inject constructor() {

    fun sort(blocks: List<TextBlock>, isRtl: Boolean = true): List<TextBlock> {
        if (blocks.size <= 1) return blocks

        val sortedRegions = mutableListOf<TextBlock>()
        val primary = blocks.sortedBy { it.boundingBox.centerY() }

        for (region in primary) {
            val rCenterY = region.boundingBox.centerY()
            val rCenterX = region.boundingBox.centerX()
            var inserted = false

            for (i in sortedRegions.indices) {
                val existing = sortedRegions[i]
                val eRect = existing.boundingBox

                // If current region is clearly below existing, keep searching
                if (rCenterY > eRect.bottom) {
                    continue
                }
                // If current region is clearly above existing, insert here
                if (rCenterY < eRect.top) {
                    sortedRegions.add(i, region)
                    inserted = true
                    break
                }

                // If within the vertical band of existing: sort by X
                if (isRtl && rCenterX > existing.boundingBox.centerX()) {
                    sortedRegions.add(i, region)
                    inserted = true
                    break
                }
                if (!isRtl && rCenterX < existing.boundingBox.centerX()) {
                    sortedRegions.add(i, region)
                    inserted = true
                    break
                }
            }

            if (!inserted) {
                sortedRegions.add(region)
            }
        }

        return sortedRegions
    }
}
