package com.hyosakura.liteioc.bean.factory.annotation

import com.hyosakura.liteioc.bean.MutablePropertyValues
import com.hyosakura.liteioc.bean.PropertyValues
import com.hyosakura.liteioc.bean.factory.support.RootBeanDefinition
import com.hyosakura.liteioc.util.ReflectionUtil
import java.beans.PropertyDescriptor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
open class InjectionMetadata {

    private val targetClass: Class<*>


    private val injectedElements: Collection<InjectedElement>

    @Volatile
    private var checkedElements: Set<InjectedElement>? = null

    companion object {


        val EMPTY: InjectionMetadata = object : InjectionMetadata(Any::class.java, emptyList()) {

            override fun needsRefresh(clazz: Class<*>): Boolean {
                return false
            }

            override fun checkConfigMembers(beanDefinition: RootBeanDefinition) {}

            override fun inject(target: Any, beanName: String?, pvs: PropertyValues?) {}

            override fun clear(pvs: PropertyValues?) {}

        }

        fun needsRefresh(metadata: InjectionMetadata?, clazz: Class<*>): Boolean {
            return metadata == null || metadata.needsRefresh(clazz)
        }

        fun forElements(elements: Collection<InjectedElement>, clazz: Class<*>): InjectionMetadata {
            return if (elements.isEmpty()) InjectionMetadata(clazz, emptyList()) else InjectionMetadata(
                clazz, elements
            )
        }

    }

    constructor(targetClass: Class<*>, elements: Collection<InjectedElement>) {
        this.targetClass = targetClass
        this.injectedElements = elements
    }

    open fun clear(pvs: PropertyValues?) {
        val checkedElements = this.checkedElements
        val elementsToIterate = checkedElements ?: this.injectedElements
        if (!elementsToIterate.isEmpty()) {
            for (element in elementsToIterate) {
                element.clearPropertySkipping(pvs)
            }
        }
    }

    protected open fun needsRefresh(clazz: Class<*>): Boolean {
        return this.targetClass != clazz
    }

    open fun checkConfigMembers(beanDefinition: RootBeanDefinition) {
        val checkedElements = LinkedHashSet<InjectedElement>(injectedElements.size)
        for (element in injectedElements) {
            val member = element.member
            checkedElements.add(element)
        }
        this.checkedElements = checkedElements
    }

    @Throws(Throwable::class)
    open fun inject(target: Any, beanName: String?, pvs: PropertyValues?) {
        val checkedElements = checkedElements
        val elementsToIterate = checkedElements ?: injectedElements
        if (!elementsToIterate.isEmpty()) {
            for (element in elementsToIterate) {
                element.inject(target, beanName, pvs)
            }
        }
    }

    abstract class InjectedElement protected constructor(
        val member: Member, val pd: PropertyDescriptor?
    ) {

        val isField: Boolean = member is Field

        @Volatile
        protected var skip: Boolean? = null

        protected val resourceType: Class<*>
            get() = if (isField) {
                (member as Field).type
            } else if (pd != null) {
                pd.propertyType
            } else {
                (member as Method).parameterTypes[0]
            }

        protected fun checkResourceType(resourceType: Class<*>) {
            if (isField) {
                val fieldType = (member as Field).type
                check(resourceType.isAssignableFrom(fieldType) || fieldType.isAssignableFrom(resourceType)) {
                    "Specified field type [" + fieldType + "] is incompatible with resource type [" + resourceType.name + "]"
                }
            } else {
                val paramType = if (pd != null) pd.propertyType else (member as Method).parameterTypes[0]
                check(resourceType.isAssignableFrom(paramType) || paramType.isAssignableFrom(resourceType)) {
                    "Specified parameter type [" + paramType + "] is incompatible with resource type [" + resourceType.name + "]"
                }
            }
        }

        @Throws(Throwable::class)
        open fun inject(target: Any, requestingBeanName: String?, pvs: PropertyValues?) {
            if (isField) {
                val field = member as Field
                ReflectionUtil.makeAccessible(field)
                field[target] = getResourceToInject(target, requestingBeanName)
            } else {
                if (checkPropertySkipping(pvs)) {
                    return
                }
                try {
                    val method = member as Method
                    ReflectionUtil.makeAccessible(method)
                    method.invoke(target, getResourceToInject(target, requestingBeanName))
                } catch (ex: InvocationTargetException) {
                    throw ex.targetException
                }
            }
        }

        protected fun checkPropertySkipping(pvs: PropertyValues?): Boolean {
            var skip = skip
            if (skip != null) {
                return skip
            }
            if (pvs == null) {
                this.skip = false
                return false
            }
            synchronized(pvs) {
                skip = this.skip
                if (skip != null) {
                    return skip!!
                }
                if (pd != null) {
                    if (pvs.contains(pd.name)) {
                        // Explicit value provided as part of the bean definition.
                        this.skip = true
                        return true
                    } else if (pvs is MutablePropertyValues) {
                        pvs.registerProcessedProperty(pd.name)
                    }
                }
                this.skip = false
                return false
            }
        }

        fun clearPropertySkipping(pvs: PropertyValues?) {
            if (pvs == null) {
                return
            }
            synchronized(pvs) {
                if (false == skip && pd != null && pvs is MutablePropertyValues) {
                    pvs.clearProcessedProperty(pd.name)
                }
            }
        }

        protected fun getResourceToInject(target: Any, requestingBeanName: String?): Any? {
            return null
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            return if (other !is InjectedElement) {
                false
            } else member == other.member
        }

        override fun hashCode(): Int {
            return member.javaClass.hashCode() * 29 + member.name.hashCode()
        }

        override fun toString(): String {
            return javaClass.simpleName + " for " + member
        }

    }
}