package com.hyosakura.liteioc.context.support

import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionReader
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.context.ApplicationContext
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
abstract class AbstractApplicationContext : ApplicationContext {
    protected lateinit var beanDefinitionReader: BeanDefinitionReader
    protected lateinit var configLocation: String
    protected val singletonObjects: MutableMap<String, Any?> = ConcurrentHashMap()

    @Throws(Exception::class)
    override fun refresh() {
        beanDefinitionReader.loadBeanDefinitions(configLocation)
    }

    @Throws(Exception::class)
    private fun finishBeanInitialization() {
        val registry: BeanDefinitionRegistry = beanDefinitionReader.registry
        val beanNames: Array<String> = registry.beanDefinitionNames
        for (beanName in beanNames) {
            getBean(beanName)
        }
    }
}