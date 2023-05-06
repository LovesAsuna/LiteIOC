package com.hyosakura.liteioc.context.event

import com.hyosakura.liteioc.context.ApplicationListener
import com.hyosakura.liteioc.core.ResolvableType

/**
 * @author LovesAsuna
 **/
interface ApplicationEventMulticaster {

    fun addApplicationListener(listener: ApplicationListener<*>)

    fun addApplicationListenerBean(listenerBeanName: String)

    fun removeApplicationListener(listener: ApplicationListener<*>)

    fun removeAllListeners()

    fun multicastEvent(event: ApplicationEvent)

    fun multicastEvent(event: ApplicationEvent, eventType: ResolvableType?)

}