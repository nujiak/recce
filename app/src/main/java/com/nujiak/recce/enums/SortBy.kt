package com.nujiak.recce.enums

import java.lang.IllegalArgumentException

/**
 * Orders in which the saved items can be sorted.
 *
 * @property index
 */
enum class SortBy(val index: Int) {
    GROUP(100),
    NAME(101),
    TIME(102);

    companion object {
        private val map = values().associateBy(SortBy::index)

        /**
         * Returns the [SortBy] at the given index
         *
         * @param index
         * @return
         */
        fun atIndex(index: Int): SortBy {
            return map[index] ?: throw IllegalArgumentException("Invalid SortBy index: $index")
        }
    }
}