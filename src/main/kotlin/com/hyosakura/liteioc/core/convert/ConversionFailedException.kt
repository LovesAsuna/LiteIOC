package com.hyosakura.liteioc.core.convert

import com.hyosakura.liteioc.core.ConversionException
import com.hyosakura.liteioc.util.ObjectUtil
import org.jetbrains.annotations.Nullable

/**
 * @author LovesAsuna
 **/
class ConversionFailedException : ConversionException{

    private val sourceType: TypeDescriptor?

    private val targetType: TypeDescriptor

    private val value: Any?

    constructor(
        sourceType: TypeDescriptor?, targetType: TypeDescriptor,
        value: Any?, cause: Throwable
    ) : super(
        "Failed to convert from type [" + sourceType + "] to type [" + targetType +
                "] for value '" + ObjectUtil.nullSafeToString(value) + "'", cause
    ){

        this.sourceType = sourceType
        this.targetType = targetType
        this.value = value
    }

    fun getSourceType(): TypeDescriptor? {
        return sourceType
    }

    fun getTargetType(): TypeDescriptor {
        return targetType
    }

    fun getValue(): Any? {
        return value
    }

}