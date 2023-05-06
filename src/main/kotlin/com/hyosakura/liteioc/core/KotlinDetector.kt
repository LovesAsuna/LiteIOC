package com.hyosakura.liteioc.core

import com.hyosakura.liteioc.util.ClassUtil
import java.lang.reflect.Method

object KotlinDetector {

    private var kotlinMetadata: Class<out Annotation>? = null

    var isKotlinReflectPresent = false

    init {
        var metadata: Class<*>?
        val classLoader = KotlinDetector::class.java.classLoader
        try {
            metadata = ClassUtil.forName("kotlin.Metadata", classLoader)
        } catch (ex: ClassNotFoundException) {
            // Kotlin API not available - no Kotlin support
            metadata = null
        }
        kotlinMetadata = metadata as Class<out Annotation>?
        isKotlinReflectPresent = ClassUtil.isPresent("kotlin.reflect.full.KClasses", classLoader)
    }


    val isKotlinPresent: Boolean
        get() = kotlinMetadata != null


    fun isKotlinType(clazz: Class<*>): Boolean {
        return kotlinMetadata != null && clazz.getDeclaredAnnotation(kotlinMetadata) != null
    }

    fun isSuspendingFunction(method: Method): Boolean {
        if (isKotlinType(method.declaringClass)) {
            val types = method.parameterTypes
            if (types.isNotEmpty() && "kotlin.coroutines.Continuation" == types[types.size - 1].name) {
                return true
            }
        }
        return false
    }
}