package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.NoSuchBeanDefinitionException
import java.util.concurrent.ConcurrentHashMap


/**
 * 注册表子实现类
 *
 * @author LovesAsuna
 */
open class SimpleBeanDefinitionRegistry : BeanDefinitionRegistry {
    private val beanDefinitionMap: MutableMap<String, BeanDefinition> = ConcurrentHashMap()

    override fun registerBeanDefinition(beanName: String, beanDefinition: BeanDefinition) {
        beanDefinitionMap[beanName] = beanDefinition
    }

    override fun removeBeanDefinition(beanName: String) {
        beanDefinitionMap.remove(beanName)
    }

    @Throws(Exception::class)
    override fun getBeanDefinition(beanName: String): BeanDefinition {
        return beanDefinitionMap[beanName] ?: throw NoSuchBeanDefinitionException(beanName)
    }

    override fun containsBeanDefinition(beanName: String): Boolean {
        return beanDefinitionMap.containsKey(beanName)
    }

    override fun getBeanDefinitionCount(): Int {
        return beanDefinitionMap.size
    }

    override fun getBeanDefinitionNames(): Array<String> {
        return beanDefinitionMap.keys.toTypedArray()
    }

}