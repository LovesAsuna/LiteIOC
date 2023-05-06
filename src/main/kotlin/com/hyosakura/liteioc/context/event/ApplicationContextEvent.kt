package com.hyosakura.liteioc.context.event

import com.hyosakura.liteioc.context.ApplicationContext

/**
 * @author LovesAsuna
 **/
abstract class ApplicationContextEvent : ApplicationEvent {

    constructor(source: ApplicationContext) : super(source)

    fun getApplicationContext(): ApplicationContext {
        return getSource() as ApplicationContext
    }

}