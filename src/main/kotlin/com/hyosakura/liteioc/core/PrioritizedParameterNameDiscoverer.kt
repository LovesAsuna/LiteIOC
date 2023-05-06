package com.hyosakura.liteioc.core

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
open class PrioritizedParameterNameDiscoverer : ParameterNameDiscoverer {

    private val parameterNameDiscoverers: MutableList<ParameterNameDiscoverer> = ArrayList(2)
    fun addDiscoverer(pnd: ParameterNameDiscoverer) {
        parameterNameDiscoverers.add(pnd)
    }

    override fun getParameterNames(method: Method): Array<String>? {
        for (pnd in parameterNameDiscoverers) {
            val result = pnd.getParameterNames(method)
            if (result != null) {
                return result
            }
        }
        return null
    }

    override fun getParameterNames(ctor: Constructor<*>): Array<String>? {
        for (pnd in parameterNameDiscoverers) {
            val result = pnd.getParameterNames(ctor)
            if (result != null) {
                return result
            }
        }
        return null
    }

}