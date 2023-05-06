package com.hyosakura.liteioc.core.env

import java.util.stream.Stream
import java.util.stream.StreamSupport

/**
 * @author LovesAsuna
 **/
interface PropertySources : Iterable<PropertySource<*>> {

    fun stream(): Stream<PropertySource<*>> {
        return StreamSupport.stream(spliterator(), false)
    }

    fun contains(name: String?): Boolean

    fun get(name: String?): PropertySource<*>?

}