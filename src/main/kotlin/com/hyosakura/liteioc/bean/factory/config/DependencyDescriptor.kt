package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.factory.BeanFactory
import com.hyosakura.liteioc.bean.factory.InjectionPoint
import com.hyosakura.liteioc.bean.factory.NoUniqueBeanDefinitionException
import com.hyosakura.liteioc.core.KotlinDetector
import com.hyosakura.liteioc.core.MethodParameter
import com.hyosakura.liteioc.core.ParameterNameDiscoverer
import com.hyosakura.liteioc.core.ResolvableType
import java.lang.invoke.TypeDescriptor
import java.lang.reflect.Field
import java.util.*
import kotlin.reflect.jvm.kotlinProperty

/**
 * @author LovesAsuna
 **/
open class DependencyDescriptor : InjectionPoint {

    private val declaringClass: Class<*>

    private var methodName: String? = null

    private var parameterTypes: Array<Class<*>>? = null

    private var parameterIndex = 0

    private var fieldName: String? = null

    private val required: Boolean

    private val eager: Boolean

    private var nestingLevel = 1

    private var containingClass: Class<*>? = null

    @Volatile
    @Transient
    private var resolvableType: ResolvableType? = null

    @Volatile
    @Transient
    private var typeDescriptor: TypeDescriptor? = null

    constructor(original: DependencyDescriptor) : super(original) {
        this.declaringClass = original.declaringClass
        this.methodName = original.methodName
        this.parameterTypes = original.parameterTypes
        this.parameterIndex = original.parameterIndex
        this.fieldName = original.fieldName
        this.containingClass = original.containingClass
        this.required = original.required
        this.eager = original.eager
        this.nestingLevel = original.nestingLevel
    }

    constructor(field: Field, required: Boolean) : this(field, required, true)

    constructor(field: Field, required: Boolean, eager: Boolean) : super(field) {
        this.declaringClass = field.declaringClass
        this.fieldName = field.name
        this.required = required
        this.eager = eager
    }

    constructor(methodParameter: MethodParameter, required: Boolean) : this(methodParameter, required, true)

    constructor(methodParameter: MethodParameter, required: Boolean, eager: Boolean) : super(methodParameter) {
        this.declaringClass = methodParameter.declaringClass
        if (methodParameter.method != null) {
            methodName = methodParameter.method!!.name
        }
        this.parameterTypes = methodParameter.executable.parameterTypes
        this.parameterIndex = methodParameter.parameterIndex
        this.containingClass = methodParameter.getContainingClass()
        this.required = required
        this.eager = eager
    }

    open fun isEager(): Boolean {
        return eager
    }

    open fun increaseNestingLevel() {
        this.nestingLevel++
        this.resolvableType = null
        if (this.methodParameter != null) {
            this.methodParameter = methodParameter!!.nested()
        }
    }

    fun getDependencyType(): Class<*> {
        return if (field != null) {
            if (nestingLevel > 1) {
                getResolvableType().getRawClass() ?: Any::class.java
            } else {
                this.field!!.type
            }
        } else {
            methodParameter!!.getNestedParameterType()
        }
    }

    open fun getDependencyName(): String? {
        return if (field != null) field!!.name else obtainMethodParameter().getParameterName()
    }

    @Throws(BeansException::class)
    open fun resolveCandidate(beanName: String, requiredType: Class<*>, beanFactory: BeanFactory): Any {
        return beanFactory.getBean(beanName)
    }

    @Throws(BeansException::class)
    open fun resolveNotUnique(type: ResolvableType, matchingBeans: Map<String, Any?>): Any? {
        throw NoUniqueBeanDefinitionException(type, matchingBeans.keys)
    }

    @Throws(BeansException::class)
    open fun resolveShortcut(beanFactory: BeanFactory): Any? {
        return null
    }

    open fun getResolvableType(): ResolvableType {
        var resolvableType = resolvableType
        if (resolvableType == null) {
            resolvableType = if (field != null) ResolvableType.forField(
                field!!,
                nestingLevel,
                containingClass
            ) else ResolvableType.forMethodParameter(obtainMethodParameter())
            this.resolvableType = resolvableType
        }
        return resolvableType
    }

    open fun isRequired(): Boolean {
        if (!required) {
            return false
        }
        return if (field != null) {
            !(field!!.type === Optional::class.java || hasNullableAnnotation() || KotlinDetector.isKotlinReflectPresent &&
                    KotlinDetector.isKotlinType(field!!.declaringClass) && KotlinDelegate.isNullable(
                field!!
            ))
        } else {
            !obtainMethodParameter().isOptional()
        }
    }

    private fun hasNullableAnnotation(): Boolean {
        for (ann in getAnnotations()) {
            if ("Nullable" == ann.javaClass.simpleName) {
                return true
            }
        }
        return false
    }

    open fun setContainingClass(containingClass: Class<*>?) {
        this.containingClass = containingClass
        this.resolvableType = null
        if (methodParameter != null) {
            methodParameter = methodParameter!!.withContainingClass(containingClass)
        }
    }

    open fun fallbackMatchAllowed(): Boolean {
        return false
    }

    open fun forFallbackMatch(): DependencyDescriptor {

        return object : DependencyDescriptor(this) {

            override fun fallbackMatchAllowed(): Boolean {
                return true
            }

        }

    }

    open fun initParameterNameDiscovery(parameterNameDiscoverer: ParameterNameDiscoverer?) {
        if (methodParameter != null) {
            methodParameter!!.initParameterNameDiscovery(parameterNameDiscoverer)
        }
    }

    private object KotlinDelegate {

        fun isNullable(field: Field): Boolean {
            val property = field.kotlinProperty
            return property != null && property.returnType.isMarkedNullable
        }

    }
}