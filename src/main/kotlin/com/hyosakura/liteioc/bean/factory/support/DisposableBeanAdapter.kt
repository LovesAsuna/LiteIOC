package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.factory.DisposableBean
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author LovesAsuna
 **/
class DisposableBeanAdapter : DisposableBean, Runnable {

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(DisposableBeanAdapter::class.java)

    }

    private val bean: Any

    private val beanName: String

    private val invokeDisposableBean: Boolean

    private val invokeAutoCloseable: Boolean

    constructor(bean: Any, beanName: String, beanDefinition: RootBeanDefinition) {
        this.bean = bean
        this.beanName = beanName
        this.invokeDisposableBean = bean is DisposableBean
        this.invokeAutoCloseable = bean is AutoCloseable
    }

    override fun destroy() {
        if (this.invokeDisposableBean) {
            if (logger.isTraceEnabled) {
                logger.trace("Invoking destroy() on bean with name '" + this.beanName + "'")
            }
            try {
                (this.bean as DisposableBean).destroy()
            } catch (ex: Throwable) {
                val msg = "Invocation of destroy method failed on bean with name '" + this.beanName + "'"
                if (logger.isDebugEnabled) {
                    logger.warn(msg, ex)
                } else {
                    logger.warn("$msg: $ex")
                }
            }
        }
        if (this.invokeAutoCloseable) {
            if (logger.isTraceEnabled) {
                logger.trace("Invoking close() on bean with name '" + this.beanName + "'")
            }
            try {
                (this.bean as AutoCloseable).close()
            } catch (ex: Throwable) {
                val msg = "Invocation of close method failed on bean with name '" + this.beanName + "'"
                if (logger.isDebugEnabled) {
                    logger.warn(msg, ex)
                } else {
                    logger.warn("$msg: $ex")
                }
            }
        }
    }

    override fun run() {
        destroy()
    }

}