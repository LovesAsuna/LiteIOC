package com.hyosakura.liteioc.bean

/**
 * @author LovesAsuna
 **/
class SimpleTypeConverter : TypeConverterSupport {

    constructor() {
        this.typeConverterDelegate = TypeConverterDelegate(this)
    }

}