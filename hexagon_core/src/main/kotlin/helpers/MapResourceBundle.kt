package com.hexagonkt.helpers

import java.util.*

/**
 * [TODO](https://github.com/hexagonkt/hexagon/issues/271).
 *
 * @property map .
 */
abstract class MapResourceBundle(val map: Map<*, *>) : ListResourceBundle() {

    val data: Array<Array<Any?>> by lazy {
        map.entries.map { arrayOf(it.key, it.value) }.toTypedArray()
    }

    override fun getContents(): Array<Array<Any?>> =
        data
}
