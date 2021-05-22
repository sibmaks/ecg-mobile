package xyz.dma.ecgmobile.utils

import java.util.*

enum class RomanNumeral(val value: Int) {
    I(1), IV(4), V(5), IX(9), X(10), XL(40), L(50), XC(90),
    C(100), CD(400), D(500), CM(900), M(1000);

    companion object {
        val reverseSortedValues: List<RomanNumeral>
            get() {
                val list = Arrays.asList(*values())
                list.sortWith { o1: RomanNumeral, o2: RomanNumeral -> -1 * o1.value.compareTo(o2.value) }
                return list
            }
    }
}