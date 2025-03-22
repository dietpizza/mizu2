package com.kepsake.mizu2.logic

class NaturalOrderComparator : Comparator<String> {
    override fun compare(s1: String, s2: String): Int {
        var i1 = 0
        var i2 = 0

        while (i1 < s1.length && i2 < s2.length) {
            val c1 = s1[i1]
            val c2 = s2[i2]

            // If both characters are digits, compare the complete numbers
            if (c1.isDigit() && c2.isDigit()) {
                // Find the complete numbers
                var num1 = 0
                var num2 = 0

                // Extract first number
                while (i1 < s1.length && s1[i1].isDigit()) {
                    num1 = num1 * 10 + (s1[i1] - '0')
                    i1++
                }

                // Extract second number
                while (i2 < s2.length && s2[i2].isDigit()) {
                    num2 = num2 * 10 + (s2[i2] - '0')
                    i2++
                }

                // If numbers differ, return their difference
                if (num1 != num2) {
                    return num1 - num2
                }
                // Otherwise continue with the rest of the strings
            } else {
                // If characters differ, return their difference
                if (c1 != c2) {
                    return c1 - c2
                }

                // Move to next characters
                i1++
                i2++
            }
        }

        // If one string is a prefix of the other, the shorter comes first
        return s1.length - s2.length
    }
}