package com.hyosakura.liteioc.core.env

/**
 * @author LovesAsuna
 **/
open class MapPropertySource : EnumerablePropertySource<Map<String, Any>> {

    constructor(name: String, source: Map<String, Any>) : super(name, source)

    override fun getProperty(name: String): Any? {
        return source[name]
    }

    override fun containsProperty(name: String): Boolean {
        return source.containsKey(name)
    }

    override fun getPropertyNames(): Array<String> {
        return source.keys.toTypedArray()
    }

}