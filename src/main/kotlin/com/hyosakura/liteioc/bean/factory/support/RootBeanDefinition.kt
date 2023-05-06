package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.core.ResolvableType
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
open class RootBeanDefinition : AbstractBeanDefinition {

    private var decoratedDefinition: BeanDefinitionHolder? = null

    @Volatile
    var stale = false

    var allowCaching = true

    var isFactoryMethodUnique = false

    @Volatile
    @JvmField
    var targetType: ResolvableType? = null

    var resolvedTargetType: Class<*>? = null

    @Volatile
    var factoryMethodReturnType: ResolvableType? = null

    @Volatile
    var factoryMethodToIntrospect: Method? = null

    val constructorArgumentLock = Any()

    var resolvedConstructorOrFactoryMethod: Executable? = null

    var resolvedConstructor: Executable? = null

    var resolvedConstructorArguments: Array<out Any?>? = null

    var preparedConstructorArguments: Array<out Any?>? = null

    var constructorArgumentsResolved = false

    val postProcessingLock = Any()

    var postProcessed = false

    constructor()

    constructor(beanClass: Class<*>?) : super() {
        setBeanClass(beanClass)
    }

    constructor(original: RootBeanDefinition) : super(original) {
        this.decoratedDefinition = original.decoratedDefinition;
        this.allowCaching = original.allowCaching;
        this.isFactoryMethodUnique = original.isFactoryMethodUnique;
        this.targetType = original.targetType;
        this.factoryMethodToIntrospect = original.factoryMethodToIntrospect;
    }

    constructor(original: BeanDefinition) : super(original)

    open fun setDecoratedDefinition(decoratedDefinition: BeanDefinitionHolder?) {
        this.decoratedDefinition = decoratedDefinition
    }

    open fun getDecoratedDefinition(): BeanDefinitionHolder? {
        return this.decoratedDefinition
    }

    open fun setTargetType(targetType: ResolvableType) {
        this.targetType = targetType
    }

    open fun setTargetType(targetType: Class<*>?) {
        this.targetType = if (targetType != null) ResolvableType.forClass(targetType) else null
    }

    open fun getTargetType(): Class<*>? {
        if (this.resolvedTargetType != null) {
            return this.resolvedTargetType
        }
        val targetType = this.targetType
        return targetType?.resolve()
    }

    override fun cloneBeanDefinition(): RootBeanDefinition {
        return RootBeanDefinition(this)
    }

    override fun setParentName(parentName: String?) {
        require(parentName == null) { "Root bean cannot be changed into a child bean with parent reference" }
    }

    override fun getParentName(): String? = null

    open fun setUniqueFactoryMethodName(name: String) {
        setFactoryMethodName(name)
        this.isFactoryMethodUnique = true
    }

    open fun setNonUniqueFactoryMethodName(name: String) {
        setFactoryMethodName(name)
        this.isFactoryMethodUnique = false
    }

    open fun isFactoryMethod(candidate: Method): Boolean {
        return candidate.name == getFactoryMethodName()
    }

    open fun setResolvedFactoryMethod(method: Method?) {
        this.factoryMethodToIntrospect = method
    }

    open fun getResolvedFactoryMethod(): Method? {
        return this.factoryMethodToIntrospect
    }

    open fun getPreferredConstructors(): Array<Constructor<*>>? {
        return null
    }

    override fun toString(): String {
        return "Root bean: " + super.toString()
    }

}