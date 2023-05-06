package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.factory.support.DefaultListableBeanFactory
import com.hyosakura.liteioc.context.support.GenericApplicationContext

/**
 * @author LovesAsuna
 **/
class AnnotationConfigApplicationContext : GenericApplicationContext, AnnotationConfigRegistry {

    private var reader: AnnotatedBeanDefinitionReader

    private var scanner: ClassPathBeanDefinitionScanner

    constructor() {
        reader = AnnotatedBeanDefinitionReader(this)
        scanner = ClassPathBeanDefinitionScanner(this)
    }

    constructor(beanFactory: DefaultListableBeanFactory) : super(beanFactory) {
        reader = AnnotatedBeanDefinitionReader(this)
        scanner = ClassPathBeanDefinitionScanner(this)
    }

    constructor(vararg componentClasses: Class<*>) : this() {
        register(*componentClasses)
        refresh()
    }

    constructor(vararg basePackages: String) : this() {
        scan(*basePackages)
        refresh()
    }

    override fun register(vararg componentClasses: Class<*>) {
        this.reader.register(*componentClasses)
    }

    override fun scan(vararg basePackages: String) {
        this.scanner.scan(*basePackages)
    }

}