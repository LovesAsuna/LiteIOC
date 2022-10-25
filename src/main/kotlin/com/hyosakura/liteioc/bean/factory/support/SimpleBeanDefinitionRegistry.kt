package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition
import java.util.concurrent.ConcurrentHashMap


/**
 * 注册表子实现类
 *
 * @author LovesAsuna
 */
class SimpleBeanDefinitionRegistry : BeanDefinitionRegistry {
    private val beanDefinitionMap: MutableMap<String, BeanDefinition> = ConcurrentHashMap()

    override fun registerBeanDefinition(beanName: String, beanDefinition: BeanDefinition) {
        beanDefinitionMap[beanName] = beanDefinition
    }

    override fun removeBeanDefinition(beanName: String) {
        beanDefinitionMap.remove(beanName)
    }

    @Throws(Exception::class)
    override fun getBeanDefinition(beanName: String): BeanDefinition? {
        return beanDefinitionMap[beanName]
    }

    override fun containsBeanDefinition(beanName: String): Boolean {
        return beanDefinitionMap.containsKey(beanName)
    }

    override val beanDefinitionCount: Int
        get() = beanDefinitionMap.size

    override val beanDefinitionNames: Array<String>
        get() = beanDefinitionMap.keys.toTypedArray()
}