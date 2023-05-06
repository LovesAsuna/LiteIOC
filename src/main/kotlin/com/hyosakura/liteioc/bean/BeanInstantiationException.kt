package com.hyosakura.liteioc.bean

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
class BeanInstantiationException : FatalBeanException {

    private var beanClass: Class<*>? = null

    private var constructor: Constructor<*>? = null

    private var constructingMethod: Method? = null

    constructor(beanClass: Class<*>, msg: String) : this(beanClass, msg, null)

    constructor(
        beanClass: Class<*>,
        msg: String,
        cause: Throwable?
    ) : super("Failed to instantiate [" + beanClass.name + "]: " + msg, cause) {
        this.beanClass = beanClass
        constructor = null
        constructingMethod = null
    }

    constructor(
        constructor: Constructor<*>,
        msg: String,
        cause: Throwable?
    ) : super("Failed to instantiate [" + constructor.declaringClass.name + "]: " + msg, cause) {
        beanClass = constructor.declaringClass
        this.constructor = constructor
        constructingMethod = null
    }

    constructor(
        constructingMethod: Method,
        msg: String,
        cause: Throwable?
    ) : super("Failed to instantiate [" + constructingMethod.returnType.name + "]: " + msg, cause) {
        beanClass = constructingMethod.returnType
        constructor = null
        this.constructingMethod = constructingMethod
    }

    fun getBeanClass(): Class<*>? {
        return beanClass
    }

    fun getConstructor(): Constructor<*>? {
        return constructor
    }

    fun getConstructingMethod(): Method? {
        return constructingMethod
    }

}