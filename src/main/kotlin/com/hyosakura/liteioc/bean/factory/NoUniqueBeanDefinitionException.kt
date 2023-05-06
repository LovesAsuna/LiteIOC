package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.core.ResolvableType

/**
 * @author LovesAsuna
 **/
class NoUniqueBeanDefinitionException : NoSuchBeanDefinitionException {

    private var numberOfBeansFound: Int

    private var beanNamesFound: Collection<String>?

    constructor(type: Class<*>, numberOfBeansFound: Int, message: String) : super(type, message) {
        this.numberOfBeansFound = numberOfBeansFound
        this.beanNamesFound = null
    }

    constructor(type: Class<*>, beanNamesFound: Collection<String>) : super(
        type, "expected single matching bean but found " + beanNamesFound.size + ": " + beanNamesFound.joinToString(",")
    ) {
        this.numberOfBeansFound = beanNamesFound.size
        this.beanNamesFound = beanNamesFound
    }

    constructor(type: ResolvableType, beanNamesFound: Collection<String>) : super(
        type,
        "expected single matching bean but found " + beanNamesFound.size + ": " + beanNamesFound.joinToString(",")
    ) {
        this.numberOfBeansFound = beanNamesFound.size
        this.beanNamesFound = beanNamesFound
    }

    constructor(type: Class<*>, vararg beanNamesFound: String) : this(type, listOf<String>(*beanNamesFound))

}