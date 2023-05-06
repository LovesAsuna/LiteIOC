package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.core.ResolvableType

/**
 * @author LovesAsuna
 **/
interface BeanFactory {

    /**
     * 返回对应name的Bean对象
     * @param name Bean的名字
     * @param args 创建实例时显式使用的参数
     * @return 对应的Bean对象
     */
    @Throws(BeansException::class)
    fun getBean(name: String, vararg args: Any?): Any

    /**
     * 返回具有确切类型的对应name的Bean对象
     * @param name Bean的名字
     * @param requiredType 对应Bean对象的class
     * @return 对应的Bean对象
     */
    @Throws(BeansException::class)
    fun <T> getBean(name: String, requiredType: Class<T>): T

    /**
     * 返回对应类型的Bean对象
     * @param requiredType Bean的类型
     * @param args 创建实例时显式使用的参数
     * @return 对应的Bean对象
     */
    @Throws(BeansException::class)
    fun <T> getBean(requiredType: Class<T>, vararg args: Any?): T

    /**
     * 返回指定bean的提供程序，允许延迟按需检索实例，包括可用性和惟一性选项
     * @return 对应的提供程序句柄
     */
    fun <T> getBeanProvider(requiredType: Class<T>): ObjectProvider<T>

    fun <T> getBeanProvider(requiredType: ResolvableType): ObjectProvider<T>

    fun containsBean(name: String): Boolean

    @Throws(NoSuchBeanDefinitionException::class)
    fun isSingleton(name: String): Boolean

    @Throws(NoSuchBeanDefinitionException::class)
    fun getType(name: String): Class<*>?

    @Throws(NoSuchBeanDefinitionException::class)
    fun isTypeMatch(name: String, typeToMatch: ResolvableType): Boolean

    @Throws(NoSuchBeanDefinitionException::class)
    fun isTypeMatch(name: String, typeToMatch: Class<*>): Boolean

}