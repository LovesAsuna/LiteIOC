package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.bean.factory.config.ConfigurableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.ConstructorArgumentValues
import com.hyosakura.liteioc.core.ResolvableType

/**
 * 用于封装Bean标签数据
 *
 * @author LovesAsuna
 */
interface BeanDefinition : BeanMetadataElement {

    companion object {

        const val SCOPE_SINGLETON: String = ConfigurableBeanFactory.SCOPE_SINGLETON

        const val SCOPE_PROTOTYPE: String = ConfigurableBeanFactory.SCOPE_PROTOTYPE

    }

    fun setScope(scope: String?)

    fun getScope(): String?

    fun setParentName(parentName: String?)

    fun getParentName(): String?

    fun setLazyInit(lazyInit: Boolean)

    fun isLazyInit(): Boolean

    fun setDependsOn(vararg dependsOn: String)

    fun getDependsOn(): Array<String>?

    fun setAutowireCandidate(autowireCandidate: Boolean)

    fun isAutowireCandidate(): Boolean

    fun setFactoryBeanName(factoryBeanName: String?)

    fun getFactoryBeanName(): String?

    fun setFactoryMethodName(factoryMethodName: String?)

    fun getFactoryMethodName(): String?

    fun getConstructorArgumentValues(): ConstructorArgumentValues

    fun hasConstructorArgumentValues(): Boolean {
        return !getConstructorArgumentValues().isEmpty()
    }

    fun getPropertyValues(): MutablePropertyValues

    fun hasPropertyValues(): Boolean {
        return !getPropertyValues().isEmpty()
    }

    fun setBeanClassName(beanClassName: String?)

    fun getBeanClassName(): String?

    fun setDescription(description: String?)

    fun getDescription(): String?

    // Read-only attributes
    fun getResolvableType(): ResolvableType

    fun isSingleton(): Boolean

    fun isPrototype(): Boolean

    fun isAbstract(): Boolean

    fun setAttribute(name: String, value: Any?)

    fun getAttribute(name: String): Any?

    fun setInitMethodName(initMethodName: String?)

    fun getInitMethodName(): String?

    fun setDestroyMethodName(destroyMethodName: String?)

    fun getDestroyMethodName(): String?

}