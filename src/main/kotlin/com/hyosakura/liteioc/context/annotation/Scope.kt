package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.factory.annotation.ScopedProxyMode

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Scope(

    val value: String = "",

    val proxyMode: ScopedProxyMode = ScopedProxyMode.DEFAULT

)
