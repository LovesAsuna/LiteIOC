package com.hyosakura.liteioc.context.support

import com.hyosakura.liteioc.bean.factory.config.ConfigurableListableBeanFactory
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.context.annotation.ConfigurationClassProcessor

object ClassScanner {

    fun invokeScan(beanFactory: ConfigurableListableBeanFactory) {
        val processor = ConfigurationClassProcessor()
        if (beanFactory is BeanDefinitionRegistry) {
            processor.postProcessBeanDefinitionRegistry(beanFactory)
        }
    }

}