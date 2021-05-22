package xyz.dma.ecgmobile.utils

import xyz.dma.ecgmobile.utils.RomanNumeral.Companion.reverseSortedValues
import java.util.*

object NumberConverter {
    fun romanToArabic(input: String): Int {
        var romanNumeral = input.uppercase(Locale.getDefault())
        var result = 0
        val romanNumerals = reverseSortedValues
        var i = 0
        while (romanNumeral.isNotEmpty() && i < romanNumerals.size) {
            val symbol = romanNumerals[i]
            if (romanNumeral.startsWith(symbol.name)) {
                result += symbol.value
                romanNumeral = romanNumeral.substring(symbol.name.length)
            } else {
                i++
            }
        }
        require(romanNumeral.isEmpty()) { "$input cannot be converted to a Roman Numeral" }
        return result
    }

    fun arabicToRoman(number: Int): String {
        var number = number
        require(!(number <= 0 || number > 4000)) { "$number is not in range (0,4000]" }
        val romanNumerals = reverseSortedValues
        var i = 0
        val sb = StringBuilder()
        while (number > 0 && i < romanNumerals.size) {
            val currentSymbol = romanNumerals[i]
            if (currentSymbol.value <= number) {
                sb.append(currentSymbol.name)
                number -= currentSymbol.value
            } else {
                i++
            }
        }
        return sb.toString()
    }
}