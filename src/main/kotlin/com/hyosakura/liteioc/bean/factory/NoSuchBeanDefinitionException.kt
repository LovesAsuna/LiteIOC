package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.core.ResolvableType

/**
 * @author LovesAsuna
 **/
open class NoSuchBeanDefinitionException : BeansException {

    private val beanName: String?

    private val resolvableType: ResolvableType?

    constructor(type: ResolvableType) : super("No qualifying bean of type '$type' available") {
        this.beanName = null
        this.resolvableType = type
    }

    constructor(name: String) : super("No bean named '$name' available") {
        this.beanName = name
        this.resolvableType = null
    }


    constructor(
        type: ResolvableType,
        message: String
    ) : super("No qualifying bean of type '$type' available: $message") {
        this.beanName = null
        this.resolvableType = type
    }

    constructor(type: Class<*>, message: String) : this(ResolvableType.forClass(type), message)

    constructor(name: String, message: String) : super("No bean named '$name' available: $message") {
        this.beanName = name
        this.resolvableType = null
    }

    constructor(type: Class<*>) : this(ResolvableType.forClass(type))

}