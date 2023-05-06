package com.hyosakura.liteioc.context.event

import com.hyosakura.liteioc.core.ResolvableType

/**
 * @author LovesAsuna
 **/
interface GenericApplicationListener : SmartApplicationListener {

    override fun supportsEventType(eventType: Class<out ApplicationEvent>): Boolean {
        return supportsEventType(ResolvableType.forClass(eventType))
    }

    fun supportsEventType(eventType: ResolvableType): Boolean

}