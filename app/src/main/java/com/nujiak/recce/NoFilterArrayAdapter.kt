package com.nujiak.recce

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * Custom ArrayAdapter for no filtering
 */
class NoFilterArrayAdapter<T>(context: Context, resource: Int, val items: List<T>) :
    ArrayAdapter<T>(context, resource, items) {

    constructor(context: Context, resource: Int, itemsArr: Array<T>) : this(context, resource, itemsArr.toList())

    private val filter = NoFilter()

    override fun getFilter(): Filter = filter

    private inner class NoFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            return FilterResults().apply {
                values = items
                count = items.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }

    }
}