package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.factory.BeanDefinitionStoreException
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder

object BeanDefinitionReaderUtils {

    @Throws(BeanDefinitionStoreException::class)
    fun registerBeanDefinition(
        definitionHolder: BeanDefinitionHolder, registry: BeanDefinitionRegistry
    ) {
        val beanName = definitionHolder.getBeanName()
        registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition())
    }

}