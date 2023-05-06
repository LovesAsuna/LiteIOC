package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.factory.support.BeanNameGenerator
import kotlin.reflect.KClass

annotation class ComponentScan(
    val basePackages: Array<String> = [],
    val basePackageClasses: Array<KClass<*>> = [],
    val nameGenerator: KClass<out BeanNameGenerator> = BeanNameGenerator::class
)
