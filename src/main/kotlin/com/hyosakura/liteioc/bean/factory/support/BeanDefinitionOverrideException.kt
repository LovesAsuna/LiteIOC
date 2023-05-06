package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.BeanDefinitionStoreException

/**
 * @author LovesAsuna
 **/
class BeanDefinitionOverrideException : BeanDefinitionStoreException {

    private val beanDefinition: BeanDefinition?

    private val existingDefinition: BeanDefinition?

    constructor(beanName: String, beanDefinition: BeanDefinition, existingDefinition: BeanDefinition) : super(
        beanName,
        "Cannot register bean definition [$beanDefinition] for bean '$beanName' since there is already [$existingDefinition] bound."
    ) {
        this.beanDefinition = beanDefinition
        this.existingDefinition = existingDefinition
    }

}