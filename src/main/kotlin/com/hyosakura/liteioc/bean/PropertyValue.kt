package com.hyosakura.liteioc.bean

/**
 * 封装Bean标签下的property标签的属性
 *
 * @author LovesAsuna
 */
class PropertyValue {

    var name: String

    var value: Any?

    var converted: Boolean = false

    private var convertedValue: Any? = null

    var optional: Boolean = false

    constructor(name: String, value: Any?) {
        this.name = name
        this.value = value
    }

    constructor(original: PropertyValue) {
        name = original.name
        value = original.value
        this.optional = original.optional
    }

    constructor(original: PropertyValue, newValue: Any?) {
        name = original.name
        value = newValue
        this.optional = original.optional
    }

    fun isConverted() = this.converted

    fun getConvertedValue(): Any? = this.convertedValue

    @Synchronized
    fun setConvertedValue(value: Any?) {
        converted = true
        this.convertedValue = value
    }

}