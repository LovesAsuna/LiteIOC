package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.core.convert.TypeDescriptor

/**
 * @author LovesAsuna
 **/
interface TypeConverter {

    @Throws(TypeMismatchException::class)
    fun <T> convertIfNecessary(value: Any?, requiredType: Class<T>?): T?

    @Throws(TypeMismatchException::class)
    fun <T> convertIfNecessary(
        value: Any?, requiredType: Class<T>?, typeDescriptor: TypeDescriptor?
    ): T? {
        throw UnsupportedOperationException("TypeDescriptor resolution not supported")
    }

}