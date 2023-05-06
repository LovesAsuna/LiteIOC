package com.hyosakura.liteioc.context

import com.hyosakura.liteioc.context.event.ApplicationEvent
import java.util.*

/**
 * @author LovesAsuna
 **/
fun interface ApplicationListener<E : ApplicationEvent> : EventListener {

    fun onApplicationEvent(event: E)

}