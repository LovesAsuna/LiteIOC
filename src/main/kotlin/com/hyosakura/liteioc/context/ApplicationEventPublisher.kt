package com.hyosakura.liteioc.context

import com.hyosakura.liteioc.context.event.ApplicationEvent

/**
 * @author LovesAsuna
 **/
fun interface ApplicationEventPublisher {

    fun publishEvent(event: ApplicationEvent) {
        publishEvent(event)
    }

    fun publishEvent(event: Any)

}