package com.hyosakura.liteioc.core

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Parameter


class StandardReflectionParameterNameDiscoverer : ParameterNameDiscoverer {
    override fun getParameterNames(method: Method): Array<String>? {
        return getParameterNames(method.parameters)
    }

    override fun getParameterNames(ctor: Constructor<*>): Array<String>? {
        return getParameterNames(ctor.parameters)
    }

    private fun getParameterNames(parameters: Array<Parameter>): Array<String>? {
        val parameterNames = arrayOfNulls<String>(parameters.size)
        for (i in parameters.indices) {
            val param = parameters[i]
            if (!param.isNamePresent) {
                return null
            }
            parameterNames[i] = param.name
        }
        @Suppress("UNCHECKED_CAST")
        return parameterNames as Array<String>
    }

}