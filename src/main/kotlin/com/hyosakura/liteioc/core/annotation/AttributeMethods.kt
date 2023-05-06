package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.util.ReflectionUtil
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
class AttributeMethods {

    companion object {

        val NONE = AttributeMethods(null, emptyArray())

        private val cache: MutableMap<Class<out Annotation>, AttributeMethods> = ConcurrentHashMap()

        private val methodComparator = Comparator { m1: Method?, m2: Method? ->
            if (m1 != null && m2 != null) {
                return@Comparator m1.name.compareTo(m2.name)
            }
            if (m1 != null) -1 else 1
        }

        fun forAnnotationType(annotationType: Class<out Annotation>?): AttributeMethods {
            return if (annotationType == null) {
                NONE
            } else cache.computeIfAbsent(annotationType) { type ->
                compute(type)
            }
        }

        private fun compute(annotationType: Class<out Annotation>): AttributeMethods {
            val methods = annotationType.declaredMethods
            var size = methods.size
            for (i in methods.indices) {
                if (!isAttributeMethod(methods[i])) {
                    methods[i] = null
                    size--
                }
            }
            if (size == 0) {
                return NONE
            }
            Arrays.sort(methods, methodComparator)
            val attributeMethods = Arrays.copyOf(methods, size)
            return AttributeMethods(annotationType, attributeMethods)
        }

        private fun isAttributeMethod(method: Method): Boolean {
            return method.parameterCount == 0 && method.returnType != Void.TYPE
        }

        fun describe(attribute: Method?): String {
            return if (attribute == null) {
                "(none)"
            } else describe(attribute.declaringClass, attribute.name)
        }

        fun describe(annotationType: Class<*>?, attributeName: String?): String {
            if (attributeName == null) {
                return "(none)"
            }
            val `in` = if (annotationType != null) " in annotation [" + annotationType.name + "]" else ""
            return "attribute '$attributeName'$`in`"
        }

    }

    private val annotationType: Class<out Annotation>?

    private val attributeMethods: Array<Method>

    private val canThrowTypeNotPresentException: BooleanArray

    private val hasDefaultValueMethod: Boolean

    private val hasNestedAnnotation: Boolean

    private constructor(annotationType: Class<out Annotation>?, attributeMethods: Array<Method>) {
        this.annotationType = annotationType
        this.attributeMethods = attributeMethods
        this.canThrowTypeNotPresentException = BooleanArray(attributeMethods.size)
        var foundDefaultValueMethod = false
        var foundNestedAnnotation = false
        for (i in attributeMethods.indices) {
            val method: Method = attributeMethods[i]
            val type = method.returnType
            if (!foundDefaultValueMethod && method.defaultValue != null) {
                foundDefaultValueMethod = true
            }
            if (!foundNestedAnnotation && (type.isAnnotation || type.isArray && type.componentType.isAnnotation)) {
                foundNestedAnnotation = true
            }
            ReflectionUtil.makeAccessible(method)
            this.canThrowTypeNotPresentException[i] =
                type == Class::class.java || type == Array<Class<*>>::javaClass || type.isEnum
        }
        this.hasDefaultValueMethod = foundDefaultValueMethod
        this.hasNestedAnnotation = foundNestedAnnotation
    }

    fun hasOnlyValueAttribute(): Boolean {
        return attributeMethods.size == 1 &&
                MergedAnnotation.VALUE == attributeMethods[0].name
    }

    fun canThrowTypeNotPresentException(index: Int): Boolean {
        return canThrowTypeNotPresentException[index]
    }


    fun hasNestedAnnotation(): Boolean {
        return hasNestedAnnotation
    }

    operator fun get(index: Int): Method {
        return attributeMethods[index]
    }

    operator fun get(name: String): Method? {
        val index: Int = indexOf(name)
        return if (index != -1) attributeMethods[index] else null
    }

    fun indexOf(attribute: Method): Int {
        for (i in attributeMethods.indices) {
            if (attributeMethods[i] == attribute) {
                return i
            }
        }
        return -1
    }

    fun indexOf(name: String): Int {
        for (i in attributeMethods.indices) {
            if (attributeMethods[i].name == name) {
                return i
            }
        }
        return -1
    }

    fun size(): Int {
        return attributeMethods.size
    }

    fun isValid(annotation: Annotation): Boolean {
        assertAnnotation(annotation)
        for (i in 0 until size()) {
            if (canThrowTypeNotPresentException(i)) {
                try {
                    get(i).invoke(annotation)
                } catch (ex: Throwable) {
                    return false
                }
            }
        }
        return true
    }

    private fun assertAnnotation(annotation: Annotation) {
        if (annotationType != null) {
            require(annotationType.isInstance(annotation))
        }
    }

}