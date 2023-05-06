package com.hyosakura.liteioc.context

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.factory.config.ConfigurableBeanFactory
import com.hyosakura.liteioc.core.env.ConfigurableEnvironment
import java.io.Closeable

/**
 * @author LovesAsuna
 **/
interface ConfigurableApplicationContext : ApplicationContext, Closeable {

    fun setId(id: String)

    fun setParent(parent: ApplicationContext?)

    fun setEnvironment(environment: ConfigurableEnvironment)

    override fun getEnvironment(): ConfigurableEnvironment

    fun addApplicationListener(listener: ApplicationListener<*>)

    @Throws(BeansException::class, IllegalStateException::class)
    fun refresh()

    fun registerShutdownHook()

    override fun close()

    fun isActive(): Boolean

    @Throws(IllegalStateException::class)
    fun getBeanFactory(): ConfigurableBeanFactory

    companion object {

        const val SHUTDOWN_HOOK_THREAD_NAME = "LiteIOCContextShutdownHook"

    }
}