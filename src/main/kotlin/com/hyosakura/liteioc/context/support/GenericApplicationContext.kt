package com.hyosakura.liteioc.context.support

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.BeanDefinitionStoreException
import com.hyosakura.liteioc.bean.factory.NoSuchBeanDefinitionException
import com.hyosakura.liteioc.bean.factory.config.ConfigurableListableBeanFactory
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.bean.factory.support.DefaultListableBeanFactory
import com.hyosakura.liteioc.context.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author LovesAsuna
 **/
open class GenericApplicationContext : AbstractApplicationContext, BeanDefinitionRegistry {

    private val beanFactory: DefaultListableBeanFactory

    private val refreshed = AtomicBoolean()

    constructor() {
        this.beanFactory = DefaultListableBeanFactory()
    }

    constructor(beanFactory: DefaultListableBeanFactory) {
        this.beanFactory = beanFactory
    }

    constructor(parent: ApplicationContext?) : this() {
        setParent(parent)
    }

    constructor(beanFactory: DefaultListableBeanFactory, parent: ApplicationContext?) : this(beanFactory) {
        setParent(parent)
    }

    override fun setParent(parent: ApplicationContext?) {
        super.setParent(parent)
        this.beanFactory.setParentBeanFactory(getInternalParentBeanFactory())
    }

    @Throws(IllegalStateException::class)
    override fun refreshBeanFactory() {
        check(
            this.refreshed.compareAndSet(
                false,
                true
            )
        ) { "GenericApplicationContext does not support multiple refresh attempts: just call 'refresh' once" }
    }

    override fun closeBeanFactory() {}

    override fun getBeanFactory(): ConfigurableListableBeanFactory = this.beanFactory

    @Throws(BeanDefinitionStoreException::class)
    override fun registerBeanDefinition(beanName: String, beanDefinition: BeanDefinition) {
        this.beanFactory.registerBeanDefinition(beanName, beanDefinition)
    }

    @Throws(NoSuchBeanDefinitionException::class)
    override fun removeBeanDefinition(beanName: String) {
        this.beanFactory.removeBeanDefinition(beanName)
    }

    @Throws(NoSuchBeanDefinitionException::class)
    override fun getBeanDefinition(beanName: String): BeanDefinition {
        return this.beanFactory.getBeanDefinition(beanName)
    }

}