package com.hyosakura.liteioc.context.support

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.factory.config.ConfigurableListableBeanFactory
import com.hyosakura.liteioc.bean.factory.support.DefaultListableBeanFactory
import com.hyosakura.liteioc.context.ApplicationContext
import com.hyosakura.liteioc.context.ApplicationContextException
import java.io.IOException

/**
 * @author LovesAsuna
 **/
abstract class AbstractRefreshableApplicationContext : AbstractApplicationContext {

    @Volatile
    private var beanFactory: DefaultListableBeanFactory? = null

    private var allowBeanDefinitionOverriding: Boolean? = null

    private val allowCircularReferences: Boolean? = null

    constructor()

    constructor(context: ApplicationContext?) : super(context)

    @Throws(BeansException::class)
    override fun refreshBeanFactory() {
        if (hasBeanFactory()) {
            destroyBeans()
            closeBeanFactory()
        }
        try {
            var beanFactory = createBeanFactory()
            customizeBeanFactory(beanFactory)
            loadBeanDefinitions(beanFactory)
        } catch (ex: IOException) {
            throw ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex)
        }
    }

    open fun hasBeanFactory(): Boolean {
        return this.beanFactory != null
    }

    override fun closeBeanFactory() {
        val beanFactory = beanFactory
        if (beanFactory != null) {
            this.beanFactory = null
        }
    }

    open fun customizeBeanFactory(beanFactory: DefaultListableBeanFactory) {
        if (this.allowBeanDefinitionOverriding != null) {
            beanFactory.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding!!)
        }
        if (this.allowCircularReferences != null) {
            beanFactory.setAllowCircularReferences(this.allowCircularReferences)
        }
    }

    open fun createBeanFactory(): DefaultListableBeanFactory {
        return DefaultListableBeanFactory(getInternalParentBeanFactory())
    }

    override fun getBeanFactory(): ConfigurableListableBeanFactory {
        return beanFactory
            ?: throw IllegalStateException(
                "BeanFactory not initialized or already closed - " +
                        "call 'refresh' before accessing beans via the ApplicationContext"
            )
    }

    @Throws(BeansException::class, IOException::class)
    protected abstract fun loadBeanDefinitions(beanFactory: DefaultListableBeanFactory)

}