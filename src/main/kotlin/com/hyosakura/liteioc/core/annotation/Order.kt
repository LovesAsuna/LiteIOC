package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.core.Ordered

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
annotation class Order(val value: Int = Ordered.LOWEST_PRECEDENCE)
