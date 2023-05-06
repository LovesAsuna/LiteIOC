package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.BeanDefinition

/**
 * @author LovesAsuna
 **/
class BeanDefinitionHolder {

    private val beanDefinition: BeanDefinition

    private val beanName: String

    constructor(beanDefinition: BeanDefinition, beanName: String) {
        this.beanDefinition = beanDefinition
        this.beanName = beanName
    }

    fun getBeanDefinition(): BeanDefinition {
        return beanDefinition
    }

    fun getBeanName(): String {
        return beanName
    }

}