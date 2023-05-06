package com.hyosakura.liteioc.aop

import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
annotation class Hook(val value: Array<KClass<out HookHandler>> = [])
