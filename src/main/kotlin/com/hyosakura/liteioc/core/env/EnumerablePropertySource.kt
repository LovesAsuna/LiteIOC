package com.hyosakura.liteioc.core.env

import com.hyosakura.liteioc.util.ObjectUtil

/**
 * @author LovesAsuna
 **/
abstract class EnumerablePropertySource<T> : PropertySource<T> {

    constructor(name: String, source: T) : super(name, source)

    protected constructor(name: String) : super(name)

    override fun containsProperty(name: String): Boolean {
        for (arrayEle in getPropertyNames()) {
            if (ObjectUtil.nullSafeEquals(arrayEle, name)) {
                return true
            }
        }
        return false
    }

    abstract fun getPropertyNames(): Array<String>

}