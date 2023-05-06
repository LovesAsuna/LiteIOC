package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.core.convert.ConversionService

/**
 * @author LovesAsuna
 **/
interface ConfigurablePropertyAccessor : PropertyAccessor, TypeConverter {

    fun setConversionService(conversionService: ConversionService?)

    fun getConversionService(): ConversionService?

}