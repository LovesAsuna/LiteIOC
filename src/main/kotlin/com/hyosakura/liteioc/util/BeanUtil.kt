package com.hyosakura.liteioc.util

import com.hyosakura.liteioc.bean.BeanInstantiationException
import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.CachedIntrospectionResults
import com.hyosakura.liteioc.core.KotlinDetector
import java.beans.PropertyDescriptor
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URI
import java.net.URL
import java.time.temporal.Temporal
import java.util.*
import kotlin.collections.set
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.kotlinFunction

/**
 * @author LovesAsuna
 **/
object BeanUtil {

    private val DEFAULT_TYPE_VALUES = mapOf(
        Boolean::class.javaPrimitiveType to false,
        Byte::class.javaPrimitiveType to 0.toByte(),
        Short::class.javaPrimitiveType to 0.toShort(),
        Int::class.javaPrimitiveType to 0,
        Long::class.javaPrimitiveType to 0L,
        Float::class.javaPrimitiveType to 0f,
        Double::class.javaPrimitiveType to 0.0,
        Char::class.javaPrimitiveType to '\u0000'
    )

    fun isSimpleProperty(type: Class<*>): Boolean {
        return isSimpleValueType(type) || type.isArray && isSimpleValueType(
            type.componentType
        )
    }

    fun isSimpleValueType(type: Class<*>): Boolean {
        return Void::class.java != type && Void.TYPE != type && (ClassUtil.isPrimitiveOrWrapper(type) || Enum::class.java.isAssignableFrom(
            type
        ) || CharSequence::class.java.isAssignableFrom(type) || Number::class.java.isAssignableFrom(type) || Date::class.java.isAssignableFrom(
            type
        ) || Temporal::class.java.isAssignableFrom(type) || URI::class.java == type || URL::class.java == type || Locale::class.java == type || Class::class.java == type)
    }

    fun <T : Any> findPrimaryConstructor(clazz: Class<T>): Constructor<T>? {
        return if (KotlinDetector.isKotlinReflectPresent && KotlinDetector.isKotlinType(clazz)) {
            KotlinDelegate.findPrimaryConstructor(clazz)
        } else null
    }

    @Throws(BeanInstantiationException::class)
    fun <T : Any> instantiateClass(clazz: Class<T>): T {
        if (clazz.isInterface) {
            throw BeanInstantiationException(clazz, "Specified class is an interface")
        }
        var ctor: Constructor<T>?
        try {
            ctor = clazz.getDeclaredConstructor()
        } catch (ex: NoSuchMethodException) {
            ctor = findPrimaryConstructor(clazz)
            if (ctor == null) {
                throw BeanInstantiationException(clazz, "No default constructor found", ex)
            }
        } catch (err: LinkageError) {
            throw BeanInstantiationException(clazz, "Unresolvable class definition", err)
        }
        return instantiateClass(ctor!!)
    }

    @Throws(BeanInstantiationException::class)
    fun <T : Any> instantiateClass(ctor: Constructor<T>, vararg args: Any?): T {
        return try {
            ReflectionUtil.makeAccessible(ctor)
            if (KotlinDetector.isKotlinType(ctor.declaringClass)) {
                KotlinDelegate.instantiateClass(ctor, *args)
            } else {
                val parameterTypes = ctor.parameterTypes
                require(
                    args.size <= parameterTypes.size,
                ) { "Can't specify more arguments than constructor parameters" }
                val argsWithDefaultValues = arrayOfNulls<Any>(args.size)
                for (i in args.indices) {
                    if (args[i] == null) {
                        val parameterType = parameterTypes[i]
                        argsWithDefaultValues[i] =
                            if (parameterType.isPrimitive) DEFAULT_TYPE_VALUES[parameterType] else null
                    } else {
                        argsWithDefaultValues[i] = args[i]
                    }
                }
                ctor.newInstance(*argsWithDefaultValues)
            }
        } catch (ex: InstantiationException) {
            throw BeanInstantiationException(ctor, "Is it an abstract class?", ex)
        } catch (ex: IllegalAccessException) {
            throw BeanInstantiationException(ctor, "Is the constructor accessible?", ex)
        } catch (ex: IllegalArgumentException) {
            throw BeanInstantiationException(ctor, "Illegal arguments for constructor", ex)
        } catch (ex: InvocationTargetException) {
            throw BeanInstantiationException(ctor, "Constructor threw exception", ex.targetException)
        }
    }

    @Throws(BeansException::class)
    fun findPropertyForMethod(method: Method, clazz: Class<*>): PropertyDescriptor? {
        val pds: Array<PropertyDescriptor> = getPropertyDescriptors(clazz)
        for (pd in pds) {
            if (method == pd.readMethod || method == pd.writeMethod) {
                return pd
            }
        }
        return null
    }

    @Throws(BeansException::class)
    fun getPropertyDescriptors(clazz: Class<*>): Array<PropertyDescriptor> {
        return CachedIntrospectionResults.forClass(clazz).getPropertyDescriptors()
    }

}

object KotlinDelegate {
    fun <T : Any> findPrimaryConstructor(clazz: Class<T>): Constructor<T>? {
        return try {
            val primaryCtor: KFunction<T> = clazz.kotlin.primaryConstructor ?: return null
            val constructor: Constructor<T> = primaryCtor.javaConstructor ?: throw IllegalStateException(
                "Failed to find Java constructor for Kotlin primary constructor: " + clazz.name
            )
            constructor
        } catch (ex: UnsupportedOperationException) {
            null
        }
    }

    @Throws(IllegalAccessException::class, InvocationTargetException::class, InstantiationException::class)
    fun <T : Any> instantiateClass(ctor: Constructor<T>, vararg args: Any?): T {
        val kotlinConstructor: KFunction<T> = ctor.kotlinFunction ?: return ctor.newInstance(*args)
        if (!Modifier.isPublic(ctor.modifiers) || !Modifier.isPublic(ctor.declaringClass.modifiers)) {
            kotlinConstructor.isAccessible = true
        }
        val parameters = kotlinConstructor.parameters
        val argParameters: MutableMap<KParameter, Any?> = HashMap(parameters.size)
        require(
            args.size <= parameters.size,
        ) { "Number of provided arguments should be less of equals than number of constructor parameters" }
        for (i in args.indices) {
            if (!(parameters[i].isOptional && args[i] == null)) {
                argParameters[parameters[i]] = args[i]
            }
        }
        return kotlinConstructor.callBy(argParameters)
    }

}