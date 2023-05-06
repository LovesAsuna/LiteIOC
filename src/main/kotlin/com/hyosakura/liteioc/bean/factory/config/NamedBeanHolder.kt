package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.factory.NamedBean

/**
 * @author LovesAsuna
 **/
class NamedBeanHolder<T> : NamedBean {

    private val beanName: String

    private val beanInstance: T

    constructor(beanName: String, beanInstance: T) {
        this.beanName = beanName
        this.beanInstance = beanInstance
    }

    override fun getBeanName(): String {
        return beanName
    }

    fun getBeanInstance(): T {
        return beanInstance
    }

}