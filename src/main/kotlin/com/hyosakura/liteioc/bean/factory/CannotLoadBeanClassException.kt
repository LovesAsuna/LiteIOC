package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.bean.FatalBeanException

/**
 * @author LovesAsuna
 **/
class CannotLoadBeanClassException : FatalBeanException {

    private val beanName: String

    private val beanClassName: String?

    constructor(beanName: String, beanClassName: String?, cause: ClassNotFoundException) : super(
        "Cannot find class [$beanClassName] for bean with name '$beanName'", cause
    ) {
        this.beanName = beanName
        this.beanClassName = beanClassName
    }

    constructor(beanName: String, beanClassName: String?, cause: LinkageError) : super(
        "Error loading class [$beanClassName] for bean with name '$beanName': problem with class file or dependent class",
        cause
    ) {
        this.beanName = beanName
        this.beanClassName = beanClassName
    }

}