package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.factory.BeanFactory
import org.jetbrains.annotations.Nullable
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
interface InstantiationStrategy {

    @Throws(BeansException::class)
    fun instantiate(
        bd: RootBeanDefinition,
        beanName: String?,
        owner: BeanFactory,
        ctor: Constructor<*>,
        vararg args: Any?
    ): Any

    @Throws(BeansException::class)
    fun instantiate(
        bd: RootBeanDefinition,
        beanName: String?,
        owner: BeanFactory,
    ): Any

    @Throws(BeansException::class)
    fun instantiate(
        bd: RootBeanDefinition, beanName: String?, owner: BeanFactory,
        factoryBean: Any?, factoryMethod: Method, vararg args: Any?
    ): Any

}