package com.hyosakura.liteioc.context

import com.hyosakura.liteioc.bean.factory.ListableBeanFactory
import com.hyosakura.liteioc.core.env.EnvironmentCapable

/**
 * @author LovesAsuna
 **/
interface ApplicationContext : EnvironmentCapable, ListableBeanFactory, ApplicationEventPublisher {

    fun getId(): String?

    fun getDisplayName(): String

    fun getParent(): ApplicationContext?

}