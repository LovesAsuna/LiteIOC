package com.hyosakura.liteioc.context.annotation

@Target(AnnotationTarget.FUNCTION)
annotation class Bean(
    val name: Array<String> = [],
    val autowireCandidate: Boolean = true,
    val initMethod: String = "",
    val destroyMethod: String = "",
)
