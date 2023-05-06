package com.hyosakura.liteioc.bean

/**
 * @author LovesAsuna
 **/
class InvalidPropertyException : FatalBeanException {

    val beanClass: Class<*>

    val propertyName: String

    constructor(beanClass: Class<*>, propertyName: String, msg: String) : this(beanClass, propertyName, msg, null)

    constructor(
        beanClass: Class<*>,
        propertyName: String,
        msg: String,
        cause: Throwable?
    ) : super("Invalid property '" + propertyName + "' of bean class [" + beanClass.name + "]: " + msg, cause) {
        this.beanClass = beanClass
        this.propertyName = propertyName
    }

}