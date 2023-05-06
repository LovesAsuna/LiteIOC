package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.core.convert.TypeDescriptor
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.StringUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.beans.BeanInfo
import java.beans.IntrospectionException
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.ProtectionDomain
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * @author LovesAsuna
 **/
class CachedIntrospectionResults {

    private val beanInfo: BeanInfo

    private val propertyDescriptors: MutableMap<String, PropertyDescriptor>

    private val typeDescriptorCache: ConcurrentMap<PropertyDescriptor, TypeDescriptor>

    companion object {

        val strongClassCache: ConcurrentMap<Class<*>, CachedIntrospectionResults> = ConcurrentHashMap(64)

        // todo ConcurrentReferenceHashMap
        val softClassCache: ConcurrentMap<Class<*>, CachedIntrospectionResults> = ConcurrentHashMap(64)

        // todo dynamically load
        private val beanInfoFactories: List<BeanInfoFactory> = listOf(ExtendedBeanInfoFactory())

        const val IGNORE_BEANINFO_PROPERTY_NAME = "spring.beaninfo.ignore"

        private val shouldIntrospectorIgnoreBeaninfoClasses =
            System.getProperty(IGNORE_BEANINFO_PROPERTY_NAME).toBoolean()

        private val logger: Logger = LoggerFactory.getLogger(
            CachedIntrospectionResults::class.java
        )

        val acceptedClassLoaders = Collections.newSetFromMap(ConcurrentHashMap<ClassLoader, Boolean>(16))

        @Throws(BeansException::class)
        fun forClass(beanClass: Class<*>): CachedIntrospectionResults {
            var results = strongClassCache[beanClass]
            if (results != null) {
                return results
            }
            results = softClassCache[beanClass]
            if (results != null) {
                return results
            }

            results = CachedIntrospectionResults(beanClass)

            val classCacheToUse: ConcurrentMap<Class<*>, CachedIntrospectionResults> = if (ClassUtil.isCacheSafe(
                    beanClass, CachedIntrospectionResults::class.java.classLoader
                ) || isClassLoaderAccepted(beanClass.classLoader)
            ) {
                strongClassCache
            } else {
                if (logger.isDebugEnabled) {
                    logger.debug("Not strongly caching class [" + beanClass.name + "] because it is not cache-safe")
                }
                softClassCache
            }

            val existing = classCacheToUse.putIfAbsent(beanClass, results)
            return existing ?: results
        }

        @Throws(IntrospectionException::class)
        private fun getBeanInfo(beanClass: Class<*>): BeanInfo {
            for (beanInfoFactory in beanInfoFactories) {
                val beanInfo = beanInfoFactory.getBeanInfo(beanClass)
                if (beanInfo != null) {
                    return beanInfo
                }
            }
            return if (shouldIntrospectorIgnoreBeaninfoClasses) Introspector.getBeanInfo(
                beanClass, Introspector.IGNORE_ALL_BEANINFO
            ) else Introspector.getBeanInfo(beanClass)
        }

        private fun isClassLoaderAccepted(classLoader: ClassLoader): Boolean {
            for (acceptedLoader in acceptedClassLoaders) {
                if (isUnderneathClassLoader(classLoader, acceptedLoader)) {
                    return true
                }
            }
            return false
        }

        private fun isUnderneathClassLoader(candidate: ClassLoader?, parent: ClassLoader): Boolean {
            if (candidate == parent) {
                return true
            }
            if (candidate == null) {
                return false
            }
            var classLoaderToCheck = candidate
            while (classLoaderToCheck != null) {
                classLoaderToCheck = classLoaderToCheck.parent
                if (classLoaderToCheck === parent) {
                    return true
                }
            }
            return false
        }
    }

    @Throws(BeansException::class)
    constructor (beanClass: Class<*>) {
        try {
            if (logger.isTraceEnabled) {
                logger.trace("Getting BeanInfo for class [" + beanClass.name + "]")
            }
            this.beanInfo = getBeanInfo(beanClass)
            if (logger.isTraceEnabled) {
                logger.trace("Caching PropertyDescriptors for class [" + beanClass.name + "]")
            }
            this.propertyDescriptors = LinkedHashMap<String, PropertyDescriptor>()
            val readMethodNames: MutableSet<String> = HashSet()

            // This call is slow so we do it once.
            val pds = this.beanInfo.propertyDescriptors
            for (pd in pds) {
                if (Class::class.java == beanClass && !(("name" == pd.name) || pd.name.endsWith("Name") && String::class.java == pd.propertyType)) {
                    // Only allow all name variants of Class properties
                    continue
                }
                if ((pd.writeMethod == null) && pd.propertyType != null && (ClassLoader::class.java.isAssignableFrom(pd.propertyType) || ProtectionDomain::class.java.isAssignableFrom(
                        pd.propertyType
                    ))
                ) {
                    // Ignore ClassLoader and ProtectionDomain read-only properties - no need to bind to those
                    continue
                }
                if (logger.isTraceEnabled) {
                    logger.trace(
                        "Found bean property '" + pd.name + "'" + (if (pd.propertyType != null) " of type [" + pd.propertyType.name + "]" else "") + if (pd.propertyEditorClass != null) "; editor [" + pd.propertyEditorClass.name + "]" else ""
                    )
                }
                var pd = pd
                pd = buildGenericTypeAwarePropertyDescriptor(beanClass, pd)
                this.propertyDescriptors.put(pd.name, pd)
                val readMethod = pd.readMethod
                if (readMethod != null) {
                    readMethodNames.add(readMethod.name)
                }
            }

            // Explicitly check implemented interfaces for setter/getter methods as well,
            // in particular for Java 8 default methods...
            var currClass: Class<*>? = beanClass
            while (currClass != null && currClass != Any::class.java) {
                introspectInterfaces(beanClass, currClass, readMethodNames)
                currClass = currClass.superclass
            }

            // Check for record-style accessors without prefix: e.g. "lastName()"
            // - accessor method directly referring to instance field of same name
            // - same convention for component accessors of Java 15 record classes
            introspectPlainAccessors(beanClass, readMethodNames)
            this.typeDescriptorCache = ConcurrentHashMap()
        } catch (ex: IntrospectionException) {
            throw FatalBeanException("Failed to obtain BeanInfo for class [" + beanClass.name + "]", ex)
        }
    }

    fun getPropertyDescriptors(): Array<PropertyDescriptor> {
        return propertyDescriptors.values.toTypedArray()
    }

    @Throws(IntrospectionException::class)
    private fun introspectPlainAccessors(beanClass: Class<*>, readMethodNames: MutableSet<String>) {
        for (method in beanClass.methods) {
            if (!propertyDescriptors.containsKey(method.name) && !readMethodNames.contains(method.name) && isPlainAccessor(
                    method
                )
            ) {
                propertyDescriptors[method.name] =
                    GenericTypeAwarePropertyDescriptor(beanClass, method.name, method, null, null)
                readMethodNames.add(method.name)
            }
        }
    }

    private fun isPlainAccessor(method: Method): Boolean {
        return if (Modifier.isStatic(method.modifiers) || method.declaringClass == Any::class.java || method.declaringClass == Class::class.java || method.parameterCount > 0 || method.returnType == Void.TYPE || ClassLoader::class.java.isAssignableFrom(
                method.returnType
            ) || ProtectionDomain::class.java.isAssignableFrom(method.returnType)
        ) {
            false
        } else try {
            // Accessor method referring to instance field of same name?
            method.declaringClass.getDeclaredField(method.name)
            true
        } catch (ex: Exception) {
            false
        }
    }

    @Throws(IntrospectionException::class)
    private fun introspectInterfaces(beanClass: Class<*>, currClass: Class<*>, readMethodNames: MutableSet<String>) {
        for (ifc in currClass.interfaces) {
            if (!ClassUtil.isJavaLanguageInterface(ifc)) {
                for (pd in getBeanInfo(ifc).propertyDescriptors) {
                    val existingPd = propertyDescriptors[pd!!.name]
                    if ((existingPd == null || existingPd.readMethod == null) && pd.readMethod != null) {
                        // GenericTypeAwarePropertyDescriptor leniently resolves a set* write method
                        // against a declared read method, so we prefer read method descriptors here.
                        var pd = pd
                        pd = buildGenericTypeAwarePropertyDescriptor(beanClass, pd)
                        if (pd!!.writeMethod == null && pd.propertyType != null && (ClassLoader::class.java.isAssignableFrom(
                                pd.propertyType
                            ) || ProtectionDomain::class.java.isAssignableFrom(pd.propertyType))
                        ) {
                            // Ignore ClassLoader and ProtectionDomain read-only properties - no need to bind to those
                            continue
                        }
                        propertyDescriptors[pd.name] = pd
                        val readMethod = pd.readMethod
                        if (readMethod != null) {
                            readMethodNames.add(readMethod.name)
                        }
                    }
                }
                introspectInterfaces(ifc, ifc, readMethodNames)
            }
        }
    }

    private fun buildGenericTypeAwarePropertyDescriptor(
        beanClass: Class<*>, pd: PropertyDescriptor
    ): PropertyDescriptor? {
        return try {
            GenericTypeAwarePropertyDescriptor(
                beanClass, pd.name, pd.readMethod, pd.writeMethod, pd.propertyEditorClass
            )
        } catch (ex: IntrospectionException) {
            throw FatalBeanException("Failed to re-introspect class [" + beanClass.name + "]", ex)
        }
    }

    fun getPropertyDescriptor(name: String): PropertyDescriptor? {
        var pd = propertyDescriptors[name]
        if (pd == null && name.isNotEmpty()) {
            // Same lenient fallback checking as in Property...
            pd = propertyDescriptors[StringUtil.uncapitalize(name)]
            if (pd == null) {
                pd = propertyDescriptors[StringUtil.capitalize(name)]
            }
        }
        return pd
    }

    fun addTypeDescriptor(pd: PropertyDescriptor, td: TypeDescriptor): TypeDescriptor {
        val existing = typeDescriptorCache.putIfAbsent(pd, td)
        return existing ?: td
    }

    fun getTypeDescriptor(pd: PropertyDescriptor): TypeDescriptor? {
        return this.typeDescriptorCache[pd]
    }
}