package com.hyosakura.liteioc.context.event

import com.hyosakura.liteioc.context.ApplicationListener
import com.hyosakura.liteioc.core.Ordered
import com.hyosakura.liteioc.core.Ordered.Companion.LOWEST_PRECEDENCE

/**
 * @author LovesAsuna
 **/
interface SmartApplicationListener : ApplicationListener<ApplicationEvent>, Ordered {

    fun supportsEventType(eventType: Class<out ApplicationEvent>): Boolean

    fun supportsSourceType(sourceType: Class<*>?): Boolean {
        return true
    }

    override fun getOrder(): Int {
        return LOWEST_PRECEDENCE
    }

    fun getListenerId(): String {
        return ""
    }

}