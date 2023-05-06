package com.hyosakura.liteioc.core.convert

import org.jetbrains.annotations.Nullable

/**
 * @author LovesAsuna
 **/
interface ConversionService {

    fun canConvert(sourceType: Class<*>?, targetType: Class<*>): Boolean

    fun canConvert(sourceType: TypeDescriptor?, targetType: TypeDescriptor): Boolean

    fun <T> convert(source: Any?, targetType: Class<T>): T?

    fun convert(source: Any?, sourceType: TypeDescriptor?, targetType: TypeDescriptor): Any?

}