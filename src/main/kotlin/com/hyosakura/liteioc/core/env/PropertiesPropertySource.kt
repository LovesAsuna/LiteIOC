package com.hyosakura.liteioc.core.env

import java.util.*

/**
 * @author LovesAsuna
 **/
class PropertiesPropertySource : MapPropertySource {

    @Suppress("UNCHECKED_CAST")
    constructor(name: String, source: Properties) : super(name, source as Map<String, Any>)

    protected constructor(name: String, source: Map<String, Any>) : super(name, source)

    override fun getPropertyNames(): Array<String> {
        synchronized(source) { return super.getPropertyNames() }
    }

}