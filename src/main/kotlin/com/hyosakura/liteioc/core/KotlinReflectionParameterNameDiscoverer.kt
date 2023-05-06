package com.hyosakura.liteioc.core

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.stream.Collectors
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.kotlinFunction

class KotlinReflectionParameterNameDiscoverer : ParameterNameDiscoverer {
    override fun getParameterNames(method: Method): Array<String>? {
        return if (!KotlinDetector.isKotlinType(method.declaringClass)) {
            null
        } else try {
            val function = method.kotlinFunction
            if (function != null) getParameterNames(function.parameters) else null
        } catch (ex: UnsupportedOperationException) {
            null
        }
    }

    override fun getParameterNames(ctor: Constructor<*>): Array<String>? {
        return if (ctor.declaringClass.isEnum || !KotlinDetector.isKotlinType(ctor.declaringClass)) {
            null
        } else try {
            val function = ctor.kotlinFunction
            if (function != null) getParameterNames(function.parameters) else null
        } catch (ex: UnsupportedOperationException) {
            null
        }
    }

    private fun getParameterNames(parameters: List<KParameter>): Array<String>? {
        val filteredParameters = parameters
            .stream() // Extension receivers of extension methods must be included as they appear as normal method parameters in Java
            .filter { p: KParameter -> KParameter.Kind.VALUE == p.kind || KParameter.Kind.EXTENSION_RECEIVER == p.kind }
            .collect(Collectors.toList())
        val parameterNames = arrayOfNulls<String>(filteredParameters.size)
        for (i in filteredParameters.indices) {
            val parameter = filteredParameters[i]
            // extension receivers are not explicitly named, but require a name for Java interoperability
            // $receiver is not a valid Kotlin identifier, but valid in Java, so it can be used here
            val name = (if (KParameter.Kind.EXTENSION_RECEIVER == parameter.kind) "\$receiver" else parameter.name)
                ?: return null
            parameterNames[i] = name
        }
        @Suppress("UNCHECKED_CAST")
        return parameterNames as Array<String>
    }

}