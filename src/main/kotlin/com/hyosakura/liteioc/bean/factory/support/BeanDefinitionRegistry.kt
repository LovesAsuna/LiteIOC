package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.NoSuchBeanDefinitionException


/**
 * 注册表
 *
 * @author LovesAsuna
 */
interface BeanDefinitionRegistry {

    /**
     * 注册Bean对象到注册表中
     *
     * @param beanName       Bean的名称
     * @param beanDefinition Bean对象
     */
    fun registerBeanDefinition(beanName: String, beanDefinition: BeanDefinition)

    /**
     * 从注册表移除指定名称的Bean对象
     *
     * @param beanName Bean的名称
     */
    fun removeBeanDefinition(beanName: String)

    /**
     * 从注册表获取指定名称的Bean对象
     *
     * @param beanName Bean的名称
     * @return BeanDefinition
     */
    @Throws(NoSuchBeanDefinitionException::class)
    fun getBeanDefinition(beanName: String): BeanDefinition

    /**
     * 注册表中是否包含指定名称的Bean对象
     *
     * @param beanName Bean的名称
     * @return boolean
     */
    fun containsBeanDefinition(beanName: String): Boolean

    /**
     * 返回注册表中Bean对象的数量
     *
     * @return boolean
     */
    fun getBeanDefinitionCount(): Int

    /**
     * 返回注册表中Bean对象的名称集合
     *
     * @return 名称集合
     */
    fun getBeanDefinitionNames(): Array<String>

}