package com.hyosakura.liteioc.util

import java.util.*

object ObjectUtil {

    const val NULL_STRING = "null"

    private const val EMPTY_STRING = ""

    private const val ARRAY_START = "{"

    private const val ARRAY_END = "}"

    private const val EMPTY_ARRAY = ARRAY_START + ARRAY_END

    private const val ARRAY_ELEMENT_SEPARATOR = ", "

    fun nullSafeEquals(o1: Any?, o2: Any?): Boolean {
        if (o1 === o2) {
            return true
        }
        if (o1 == null || o2 == null) {
            return false
        }
        if (o1 == o2) {
            return true
        }
        return if (o1.javaClass.isArray && o2.javaClass.isArray) {
            arrayEquals(o1, o2)
        } else false
    }

    private fun arrayEquals(o1: Any, o2: Any): Boolean {
        if (o1 is Array<*> && o2 is Array<*>) {
            return o1.contentEquals(o2)
        }
        if (o1 is BooleanArray && o2 is BooleanArray) {
            return o1.contentEquals(o2)
        }
        if (o1 is ByteArray && o2 is ByteArray) {
            return o1.contentEquals(o2)
        }
        if (o1 is CharArray && o2 is CharArray) {
            return o1.contentEquals(o2)
        }
        if (o1 is DoubleArray && o2 is DoubleArray) {
            return o1.contentEquals(o2)
        }
        if (o1 is FloatArray && o2 is FloatArray) {
            return o1.contentEquals(o2)
        }
        if (o1 is IntArray && o2 is IntArray) {
            return o1.contentEquals(o2)
        }
        if (o1 is LongArray && o2 is LongArray) {
            return o1.contentEquals(o2)
        }
        return if (o1 is ShortArray && o2 is ShortArray) {
            o1.contentEquals(o2)
        } else false
    }

    fun nullSafeHashCode(obj: Any?): Int {
        if (obj == null) {
            return 0
        }
        if (obj.javaClass.isArray) {
            if (obj is Array<*>) {
                return nullSafeHashCode(obj)
            }
            if (obj is BooleanArray) {
                return nullSafeHashCode(obj)
            }
            if (obj is ByteArray) {
                return nullSafeHashCode(obj)
            }
            if (obj is CharArray) {
                return nullSafeHashCode(obj)
            }
            if (obj is DoubleArray) {
                return nullSafeHashCode(obj)
            }
            if (obj is FloatArray) {
                return nullSafeHashCode(obj)
            }
            if (obj is IntArray) {
                return nullSafeHashCode(obj)
            }
            if (obj is LongArray) {
                return nullSafeHashCode(obj)
            }
            if (obj is ShortArray) {
                return nullSafeHashCode(obj)
            }
        }
        return obj.hashCode()
    }

    fun containsElement(array: Array<Any?>?, element: Any?): Boolean {
        if (array == null) {
            return false
        }
        for (arrayEle in array) {
            if (nullSafeEquals(arrayEle, element)) {
                return true
            }
        }
        return false
    }

    fun nullSafeClassName(obj: Any?): String {
        return if (obj != null) obj.javaClass.name else NULL_STRING
    }

    fun identityToString(obj: Any?): String {
        return if (obj == null) {
            EMPTY_STRING
        } else obj.javaClass.name + "@" + getIdentityHexString(obj)
    }

    fun getIdentityHexString(obj: Any?): String? {
        return Integer.toHexString(System.identityHashCode(obj))
    }

    fun nullSafeToString(obj: Any?): String? {
        if (obj == null) {
            return NULL_STRING
        }
        if (obj is String) {
            return obj
        }
        if (obj is Array<*> && obj.isArrayOf<Any>()) {
            return nullSafeToString(obj as Array<Any>?)
        }

        if (obj is BooleanArray) {
            return Arrays.toString(obj)
        }
        if (obj is ByteArray) {
            return Arrays.toString(obj)
        }
        if (obj is CharArray) {
            return Arrays.toString(obj)
        }
        if (obj is DoubleArray) {
            return Arrays.toString(obj)
        }
        if (obj is FloatArray) {
            return Arrays.toString(obj)
        }
        if (obj is IntArray) {
            return Arrays.toString(obj)
        }
        if (obj is LongArray) {
            return Arrays.toString(obj)
        }
        if (obj is ShortArray) {
            return Arrays.toString(obj)
        }
        return obj.toString()
    }

    fun nullSafeToString(array: Array<Any>?): String {
        if (array == null) {
            return NULL_STRING
        }
        val length = array.size
        if (length == 0) {
            return EMPTY_ARRAY
        }
        val stringJoiner = StringJoiner(
            ARRAY_ELEMENT_SEPARATOR,
            ARRAY_START,
            ARRAY_END
        )
        for (o in array) {
            stringJoiner.add(o.toString())
        }
        return stringJoiner.toString()
    }
}