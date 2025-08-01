package nz.eloque.foss_wallet.model.field

import nz.eloque.foss_wallet.parsing.InstantParser
import nz.eloque.foss_wallet.utils.inIgnoreCase
import nz.eloque.foss_wallet.utils.prettyDate
import nz.eloque.foss_wallet.utils.prettyDateTime
import nz.eloque.foss_wallet.utils.prettyTime
import java.time.Instant
import java.time.format.FormatStyle

sealed class PassContent(val id: Int) {

    data class Plain(val text: String) : PassContent(PLAIN) {
        override fun contains(query: String) = query inIgnoreCase text
        override fun prettyPrint(): String = text
        override fun isEmpty(): Boolean = text.isEmpty()
    }

    data class Currency(val amount: String, val currency: String) : PassContent(CURRENCY) {
        override fun contains(query: String) = query inIgnoreCase amount || query inIgnoreCase currency
        override fun prettyPrint(): String = amount + toCurrency(currency)
        override fun isEmpty(): Boolean = amount.isEmpty()

        private fun toCurrency(currencyCode: String): String? {
            return try {
                java.util.Currency.getInstance(currency).symbol
            } catch (_: IllegalArgumentException) {
                " $currencyCode"
            }
        }
    }

    data class Date(val date: Instant, val format: FormatStyle) : PassContent(DATE) {
        override fun contains(query: String) = query inIgnoreCase date.prettyDate(format)
        override fun prettyPrint(): String  = date.prettyDate(format)
        override fun isEmpty(): Boolean = false
    }

    data class Time(val time: Instant, val format: FormatStyle) : PassContent(TIME) {
        override fun contains(query: String) = query inIgnoreCase time.prettyTime(format)
        override fun prettyPrint(): String  = time.prettyTime(format)
        override fun isEmpty(): Boolean = false
    }

    data class DateTime(val dateTime: Instant, val format: FormatStyle) : PassContent(DATE_TIME) {
        override fun contains(query: String) = query inIgnoreCase dateTime.prettyDateTime(format)
        override fun prettyPrint(): String  = dateTime.prettyDateTime(format)
        override fun isEmpty(): Boolean = false
    }


    companion object {
        const val PLAIN = 0
        const val CURRENCY = 1
        const val DATE = 2
        const val TIME = 3
        const val DATE_TIME = 4

        fun deserialize(content: String): PassContent {
            return if (content.length >= 2 && content[0].isDigit() && content[1] == '|') {
                val id = content[0].digitToInt()
                val content = content.substring(2)
                return when(id) {
                    CURRENCY -> content.split("|").let { Currency(it[0], it[1]) }
                    DATE -> content.split("|").let { Date(InstantParser.parse(it[0]), FormatStyle.valueOf(it[1])) }
                    TIME -> content.split("|").let { Time(InstantParser.parse(it[0]), FormatStyle.valueOf(it[1])) }
                    DATE_TIME -> content.split("|").let { DateTime(InstantParser.parse(it[0]), FormatStyle.valueOf(it[1])) }
                    else -> Plain(content)
                }
            } else {
                Plain(content)
            }
        }
    }

    fun serialize(): String {
        return when (this) {
            is Plain -> this.id.toString() + "|" + this.text
            is Currency -> this.id.toString() + "|" + this.amount + "|" + this.currency
            is Date -> this.id.toString() + "|" + this.date + "|" + this.format.name
            is Time -> this.id.toString() + "|" + this.time + "|" + this.format.name
            is DateTime -> this.id.toString() + "|" + this.dateTime + "|" + this.format.name
        }
    }

    abstract fun contains(query: String): Boolean
    abstract fun prettyPrint(): String
    abstract fun isEmpty(): Boolean
    fun isNotEmpty() = !isEmpty()
}