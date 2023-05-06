package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.bean.FatalBeanException

/**
 * @author LovesAsuna
 **/
open class BeanDefinitionStoreException : FatalBeanException {

    private var resourceDescription: String?

    private var beanName: String?

    constructor(msg: String) : super(msg) {
        this.resourceDescription = null
        this.beanName = null
    }

    constructor(msg: String, cause: Throwable?) : super(msg, cause) {
        this.resourceDescription = null
        this.beanName = null
    }

    constructor(resourceDescription: String?, msg: String) : super(msg) {
        this.resourceDescription = resourceDescription
        this.beanName = null
    }

    constructor(resourceDescription: String?, msg: String, cause: Throwable?) : super(msg, cause) {
        this.resourceDescription = resourceDescription
        this.beanName = null
    }

    constructor(resourceDescription: String?, beanName: String, msg: String) : this(
        resourceDescription,
        beanName,
        msg,
        null
    )

    constructor(
        resourceDescription: String?, beanName: String, msg: String, cause: Throwable?
    ) : super(
        "Invalid bean definition with name '$beanName' defined in $resourceDescription: $msg",
        cause
    ) {
        this.resourceDescription = resourceDescription
        this.beanName = beanName
    }

}