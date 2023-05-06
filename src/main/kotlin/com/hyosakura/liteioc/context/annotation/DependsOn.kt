package com.hyosakura.liteioc.context.annotation

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
annotation class DependsOn(val value: Array<String> = [])
