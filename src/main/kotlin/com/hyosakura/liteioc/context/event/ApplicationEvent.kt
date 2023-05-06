package com.hyosakura.liteioc.context.event

import java.util.*

/**
 * @author LovesAsuna
 **/
abstract class ApplicationEvent(source: Any) : EventObject(source) {

    val timestamp: Long = System.currentTimeMillis()

}