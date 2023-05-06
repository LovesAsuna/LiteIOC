package com.hyosakura.liteioc.core.convert

import com.hyosakura.liteioc.core.ConversionException

/**
 * @author LovesAsuna
 **/
class ConverterNotFoundException : ConversionException{

    private val sourceType: TypeDescriptor?

    private val targetType: TypeDescriptor

    constructor(sourceType: TypeDescriptor?, targetType: TypeDescriptor): super("No converter found capable of converting from type [$sourceType] to type [$targetType]") {
        this.sourceType = sourceType
        this.targetType = targetType
    }

    fun getSourceType(): TypeDescriptor? {
        return sourceType
    }

    fun getTargetType(): TypeDescriptor {
        return targetType
    }

}