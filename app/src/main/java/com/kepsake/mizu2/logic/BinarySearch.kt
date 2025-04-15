package com.kepsake.mizu2.logic

import com.kepsake.mizu2.activities.ViewOffsetMap

fun computeVisibleIndex(currentY: Int, itemOffsetList: List<ViewOffsetMap>): Int {
    if (itemOffsetList.isEmpty()) return -1

    // Handle single item case
    if (itemOffsetList.size == 1) return 0

    // Edge cases - if currentY is beyond list bounds
    if (currentY <= itemOffsetList[0].offset) return 0
    if (currentY >= itemOffsetList.last().offset) return itemOffsetList.lastIndex

    // Binary search to find the closest item to currentY
    var left = 0
    var right = itemOffsetList.lastIndex

    while (left <= right) {
        val mid = left + (right - left) / 2
        val midOffset = itemOffsetList[mid].offset

        when {
            midOffset == currentY -> return mid
            midOffset < currentY -> {
                // Check if this is the closest one or we need to go right
                if (mid < itemOffsetList.lastIndex && itemOffsetList[mid + 1].offset > currentY) {
                    // Found the closest item
                    return if (currentY - midOffset < itemOffsetList[mid + 1].offset - currentY) mid else mid + 1
                }
                left = mid + 1
            }

            else -> {
                // Check if this is the closest one or we need to go left
                if (mid > 0 && itemOffsetList[mid - 1].offset < currentY) {
                    // Found the closest item
                    return if (midOffset - currentY < currentY - itemOffsetList[mid - 1].offset) mid else mid - 1
                }
                right = mid - 1
            }
        }
    }

    return left.coerceIn(0, itemOffsetList.lastIndex)
}
