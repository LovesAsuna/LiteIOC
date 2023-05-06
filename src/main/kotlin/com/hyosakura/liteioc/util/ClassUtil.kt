package com.hyosakura.liteioc.util

import org.jetbrains.annotations.Nullable
import java.io.Closeable
import java.io.Externalizable
import java.io.Serializable
import java.lang.reflect.Array
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.*

object ClassUtil {

    const val ARRAY_SUFFIX = "[]"

    private const val INTERNAL_ARRAY_PREFIX = "["

    private const val NON_PRIMITIVE_ARRAY_PREFIX = "[L"

    private val EMPTY_CLASS_ARRAY = arrayOf<Class<*>>()

    private const val PACKAGE_SEPARATOR = '.'

    private const val PATH_SEPARATOR = '/'

    private const val NESTED_CLASS_SEPARATOR = '$'

    const val BYTEBUDDY_CLASS_SEPARATOR = "ByteBuddy"

    const val CLASS_FILE_SUFFIX = ".class"

    private var javaLanguageInterfaces: Set<Class<*>>? = null

    private val primitiveWrapperTypeMap: MutableMap<Class<*>, Class<*>> = IdentityHashMap(9)

    private val primitiveTypeToWrapperMap: MutableMap<Class<*>, Class<*>> = IdentityHashMap(9)

    private val primitiveTypeNameMap: MutableMap<String, Class<*>> = HashMap(32)

    private val commonClassCache: MutableMap<String, Class<*>> = HashMap(64)

    init {
        primitiveWrapperTypeMap[Boolean::class.javaObjectType] = Boolean::class.java
        primitiveWrapperTypeMap[Byte::class.javaObjectType] = Byte::class.java
        primitiveWrapperTypeMap[Char::class.javaObjectType] = Char::class.java
        primitiveWrapperTypeMap[Double::class.javaObjectType] = Double::class.java
        primitiveWrapperTypeMap[Float::class.javaObjectType] = Float::class.java
        primitiveWrapperTypeMap[Int::class.javaObjectType] = Int::class.java
        primitiveWrapperTypeMap[Long::class.javaObjectType] = Long::class.java
        primitiveWrapperTypeMap[Short::class.javaObjectType] = Short::class.java
        primitiveWrapperTypeMap[Void::class.javaObjectType] = Void.TYPE

        // Map entry iteration is less expensive to initialize than forEach with lambdas
        for ((key, value) in primitiveWrapperTypeMap.entries) {
            primitiveTypeToWrapperMap[value] = key
            registerCommonClasses(key)
        }

        val primitiveTypes: MutableSet<Class<*>> = HashSet(32)
        primitiveTypes.addAll(primitiveWrapperTypeMap.values)
        Collections.addAll(
            primitiveTypes,
            BooleanArray::class.java,
            ByteArray::class.java,
            CharArray::class.java,
            DoubleArray::class.java,
            FloatArray::class.java,
            IntArray::class.java,
            LongArray::class.java,
            ShortArray::class.java
        )
        for (primitiveType in primitiveTypes) {
            primitiveTypeNameMap[primitiveType.name] = primitiveType
        }
        val javaLanguageInterfaceArray = arrayOf(
            Serializable::class.java,
            Externalizable::class.java,
            Closeable::class.java,
            AutoCloseable::class.java,
            Cloneable::class.java,
            Comparable::class.java
        )
        javaLanguageInterfaces = HashSet(listOf(*javaLanguageInterfaceArray))
    }

    fun isJavaLanguageInterface(ifc: Class<*>?): Boolean {
        return javaLanguageInterfaces!!.contains(ifc)
    }

    @Throws(ClassNotFoundException::class, LinkageError::class)
    fun forName(name: String, classLoader: ClassLoader?): Class<*> {
        var clazz = resolvePrimitiveClassName(name)
        if (clazz == null) {
            clazz = commonClassCache[name]
        }
        if (clazz != null) {
            return clazz
        }

        // "java.lang.String[]" style arrays
        if (name.endsWith(ARRAY_SUFFIX)) {
            val elementClassName = name.substring(0, name.length - ARRAY_SUFFIX.length)
            val elementClass = forName(elementClassName, classLoader)
            return Array.newInstance(elementClass, 0).javaClass
        }

        // "[Ljava.lang.String;" style arrays
        if (name.startsWith(NON_PRIMITIVE_ARRAY_PREFIX) && name.endsWith(";")) {
            val elementName = name.substring(NON_PRIMITIVE_ARRAY_PREFIX.length, name.length - 1)
            val elementClass = forName(elementName, classLoader)
            return Array.newInstance(elementClass, 0).javaClass
        }

        // "[[I" or "[[Ljava.lang.String;" style arrays
        if (name.startsWith(INTERNAL_ARRAY_PREFIX)) {
            val elementName: String = name.substring(INTERNAL_ARRAY_PREFIX.length)
            val elementClass = forName(elementName, classLoader)
            return Array.newInstance(elementClass, 0).javaClass
        }
        var clToUse = classLoader
        if (clToUse == null) {
            clToUse = getDefaultClassLoader()
        }
        return try {
            Class.forName(name, false, clToUse)
        } catch (ex: ClassNotFoundException) {
            val lastDotIndex: Int = name.lastIndexOf(PACKAGE_SEPARATOR)
            if (lastDotIndex != -1) {
                val nestedClassName = name.substring(
                    0, lastDotIndex
                ) + NESTED_CLASS_SEPARATOR + name.substring(lastDotIndex + 1)
                try {
                    return Class.forName(nestedClassName, false, clToUse)
                } catch (ex2: ClassNotFoundException) {
                    // Swallow - let original exception get through
                }
            }
            throw ex
        }
    }

    fun matchesTypeName(clazz: Class<*>, @Nullable typeName: String?): Boolean {
        return typeName != null && (typeName == clazz.typeName || typeName == clazz.simpleName)
    }

    fun isPresent(className: String, @Nullable classLoader: ClassLoader?): Boolean {
        return try {
            forName(className, classLoader)
            true
        } catch (err: IllegalAccessError) {
            throw IllegalStateException(
                "Readability mismatch in inheritance hierarchy of class [" + className + "]: " + err.message, err
            )
        } catch (ex: Throwable) {
            // Typically ClassNotFoundException or NoClassDefFoundError...
            false
        }
    }

    fun getQualifiedName(clazz: Class<*>): String {
        return clazz.typeName
    }

    fun isInnerClass(clazz: Class<*>): Boolean {
        return clazz.isMemberClass && !isStaticClass(clazz)
    }

    fun isStaticClass(clazz: Class<*>): Boolean {
        return Modifier.isStatic(clazz.modifiers)
    }

    fun hasMethod(clazz: Class<*>, method: Method): Boolean {
        if (clazz == method.declaringClass) {
            return true
        }
        val methodName = method.name
        val paramTypes = method.parameterTypes
        return getMethodOrNull(clazz, methodName, paramTypes) != null
    }

    fun isPrimitiveOrWrapper(clazz: Class<*>): Boolean {
        return clazz.isPrimitive || isPrimitiveWrapper(clazz)
    }

    fun isPrimitiveWrapper(clazz: Class<*>?): Boolean {
        return primitiveWrapperTypeMap.containsKey(clazz)
    }

    fun resolvePrimitiveClassName(@Nullable name: String?): Class<*>? {
        var result: Class<*>? = null
        // Most class names will be quite long, considering that they
        // SHOULD sit in a package, so a length check is worthwhile.
        if (name != null && name.length <= 7) {
            // Could be a primitive - likely.
            result = primitiveTypeNameMap[name]
        }
        return result
    }

    private fun registerCommonClasses(vararg commonClasses: Class<*>) {
        for (clazz in commonClasses) {
            commonClassCache[clazz.name] = clazz
        }
    }

    fun getDefaultClassLoader(): ClassLoader? {
        var cl: ClassLoader? = null
        try {
            cl = Thread.currentThread().contextClassLoader
        } catch (ex: Throwable) {
            // Cannot access thread context ClassLoader - falling back...
        }
        if (cl == null) {
            // No thread context class loader -> use class loader of this class.
            cl = ClassUtil::class.java.classLoader
            if (cl == null) {
                // getClassLoader() returning null indicates the bootstrap ClassLoader
                try {
                    cl = ClassLoader.getSystemClassLoader()
                } catch (ex: Throwable) {
                    // Cannot access system ClassLoader - oh well, maybe the caller can live with null...
                }
            }
        }
        return cl
    }

    fun getDescriptiveType(@Nullable value: Any?): String? {
        if (value == null) {
            return null
        }
        val clazz: Class<*> = value.javaClass
        return if (Proxy.isProxyClass(clazz)) {
            val prefix = clazz.name + " implementing "
            val result = StringJoiner(",", prefix, "")
            for (ifc in clazz.interfaces) {
                result.add(ifc.name)
            }
            result.toString()
        } else {
            clazz.typeName
        }
    }

    fun getMethodCountForName(clazz: Class<*>, methodName: String): Int {
        var count = 0
        val declaredMethods = clazz.declaredMethods
        for (method in declaredMethods) {
            if (methodName == method.name) {
                count++
            }
        }
        val ifcs = clazz.interfaces
        for (ifc in ifcs) {
            count += getMethodCountForName(ifc, methodName)
        }
        if (clazz.superclass != null) {
            count += getMethodCountForName(clazz.superclass, methodName)
        }
        return count
    }

    fun determineCommonAncestor(clazz1: Class<*>?, clazz2: Class<*>?): Class<*>? {
        if (clazz1 == null) {
            return clazz2
        }
        if (clazz2 == null) {
            return clazz1
        }
        if (clazz1.isAssignableFrom(clazz2)) {
            return clazz1
        }
        if (clazz2.isAssignableFrom(clazz1)) {
            return clazz2
        }
        var ancestor = clazz1
        do {
            ancestor = ancestor!!.superclass
            if (ancestor == null || Any::class.java == ancestor) {
                return null
            }
        } while (!ancestor!!.isAssignableFrom(clazz2))
        return ancestor
    }

    fun isAssignableValue(type: Class<*>, value: Any?): Boolean {
        return if (value != null) isAssignable(
            type, value.javaClass
        ) else !type.isPrimitive
    }

    fun isAssignable(lhsType: Class<*>, rhsType: Class<*>): Boolean {
        if (lhsType.isAssignableFrom(rhsType)) {
            return true
        }
        return if (lhsType.isPrimitive) {
            val resolvedPrimitive = primitiveWrapperTypeMap[rhsType]
            lhsType == resolvedPrimitive
        } else {
            val resolvedWrapper = primitiveTypeToWrapperMap[rhsType]
            resolvedWrapper != null && lhsType.isAssignableFrom(resolvedWrapper)
        }
    }

    fun getUserClass(instance: Any): Class<*> {
        return getUserClass(instance.javaClass)
    }

    fun getUserClass(clazz: Class<*>): Class<*> {
        if (clazz.name.contains(BYTEBUDDY_CLASS_SEPARATOR)) {
            val superclass = clazz.superclass
            if (superclass != null && superclass != Any::class.java) {
                return superclass
            }
        }
        return clazz
    }

    fun classPackageAsResourcePath(clazz: Class<*>?): String {
        if (clazz == null) {
            return ""
        }
        val className = clazz.name
        val packageEndIndex: Int = className.lastIndexOf(PACKAGE_SEPARATOR)
        if (packageEndIndex == -1) {
            return ""
        }
        val packageName = className.substring(0, packageEndIndex)
        return packageName.replace(
            PACKAGE_SEPARATOR, PATH_SEPARATOR
        )
    }

    fun getShortName(className: String): String {
        require(className.isNotEmpty()) { "Class name must not be empty" }
        val lastDotIndex: Int = className.lastIndexOf(PACKAGE_SEPARATOR)
        var nameEndIndex: Int = className.indexOf(BYTEBUDDY_CLASS_SEPARATOR)
        if (nameEndIndex == -1) {
            nameEndIndex = className.length
        }
        var shortName = className.substring(lastDotIndex + 1, nameEndIndex)
        shortName = shortName.replace(
            NESTED_CLASS_SEPARATOR, PACKAGE_SEPARATOR
        )
        return shortName
    }

    fun getPackageName(clazz: Class<*>): String {
        return getPackageName(clazz.name)
    }

    fun getPackageName(fqClassName: String): String {
        val lastDotIndex: Int = fqClassName.lastIndexOf(PACKAGE_SEPARATOR)
        return if (lastDotIndex != -1) fqClassName.substring(0, lastDotIndex) else ""
    }

    fun isCacheSafe(clazz: Class<*>, classLoader: ClassLoader?): Boolean {
        try {
            var target = clazz.classLoader
            // Common cases
            if (target === classLoader || target == null) {
                return true
            }
            if (classLoader == null) {
                return false
            }
            // Check for match in ancestors -> positive
            var current = classLoader
            while (current != null) {
                current = current.parent
                if (current === target) {
                    return true
                }
            }
            // Check for match in children -> negative
            while (target != null) {
                target = target.parent
                if (target === classLoader) {
                    return false
                }
            }
        } catch (ex: SecurityException) {
            // Fall through to loadable check below
        }

        // Fallback for ClassLoaders without parent/child relationship:
        // safe if same Class can be loaded from given ClassLoader
        return classLoader != null && isLoadable(clazz, classLoader)
    }

    private fun isLoadable(clazz: Class<*>, classLoader: ClassLoader): Boolean {
        return try {
            clazz == classLoader.loadClass(clazz.name)
            // Else: different class with same name found
        } catch (ex: ClassNotFoundException) {
            // No corresponding class found at all
            false
        }
    }

    fun getAllInterfacesForClass(clazz: Class<*>): kotlin.Array<Class<*>> {
        return getAllInterfacesForClass(clazz, null)
    }

    fun getAllInterfacesForClass(clazz: Class<*>, classLoader: ClassLoader?): kotlin.Array<Class<*>> {
        return getAllInterfacesForClassAsSet(
            clazz,
            classLoader
        ).toTypedArray()
    }

    fun getAllInterfacesForClassAsSet(clazz: Class<*>, classLoader: ClassLoader?): Set<Class<*>> {
        if (clazz.isInterface && isVisible(clazz, classLoader)) {
            return setOf(clazz)
        }
        val interfaces: MutableSet<Class<*>> = LinkedHashSet()
        var current: Class<*>? = clazz
        while (current != null) {
            val ifcs = current.interfaces
            for (ifc in ifcs) {
                if (isVisible(ifc, classLoader)) {
                    interfaces.add(ifc)
                }
            }
            current = current.superclass
        }
        return interfaces
    }

    fun isVisible(clazz: Class<*>, classLoader: ClassLoader?): Boolean {
        if (classLoader == null) {
            return true
        }
        try {
            if (clazz.classLoader === classLoader) {
                return true
            }
        } catch (ex: SecurityException) {
            // Fall through to loadable check below
        }

        // Visible if same Class can be loaded from given ClassLoader
        return isLoadable(clazz, classLoader)
    }

    fun resolvePrimitiveIfNecessary(clazz: Class<*>): Class<*> {
        return if (clazz.isPrimitive && clazz != Void.TYPE) primitiveTypeToWrapperMap[clazz]!! else clazz
    }

    fun getMethodIfAvailable(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method? {
        return if (paramTypes.isNotEmpty()) {
            getMethodOrNull(clazz, methodName, paramTypes)
        } else {
            val candidates = findMethodCandidatesByName(clazz, methodName)
            if (candidates.size == 1) {
                candidates.iterator().next()
            } else null
        }
    }

    private fun getMethodOrNull(clazz: Class<*>, methodName: String, paramTypes: kotlin.Array<out Class<*>>): Method? {
        return try {
            clazz.getMethod(methodName, *paramTypes)
        } catch (ex: NoSuchMethodException) {
            null
        }
    }

    private fun findMethodCandidatesByName(clazz: Class<*>, methodName: String): Set<Method> {
        val candidates: MutableSet<Method> = HashSet(1)
        val methods = clazz.methods
        for (method in methods) {
            if (methodName == method.name) {
                candidates.add(method)
            }
        }
        return candidates
    }

    @Throws(IllegalArgumentException::class)
    fun resolveClassName(className: String, @Nullable classLoader: ClassLoader?): Class<*>? {
        return try {
            forName(className, classLoader)
        } catch (err: IllegalAccessError) {
            throw IllegalStateException(
                "Readability mismatch in inheritance hierarchy of class [" +
                        className + "]: " + err.message, err
            )
        } catch (err: LinkageError) {
            throw IllegalArgumentException("Unresolvable class definition for class [$className]", err)
        } catch (ex: ClassNotFoundException) {
            throw IllegalArgumentException("Could not find class [$className]", ex)
        }
    }

    fun getMostSpecificMethod(method: Method, targetClass: Class<*>?): Method {
        if (targetClass != null && targetClass != method.declaringClass && isOverridable(
                method,
                targetClass
            )
        ) {
            try {
                return if (Modifier.isPublic(method.modifiers)) {
                    try {
                        targetClass.getMethod(method.name, *method.parameterTypes)
                    } catch (ex: NoSuchMethodException) {
                        method
                    }
                } else {
                    val specificMethod = ReflectionUtil.findMethod(targetClass, method.name, *method.parameterTypes)
                    specificMethod ?: method
                }
            } catch (ex: SecurityException) {
                // Security settings are disallowing reflective access; fall back to 'method' below.
            }
        }
        return method
    }

    private fun isOverridable(method: Method, targetClass: Class<*>?): Boolean {
        if (Modifier.isPrivate(method.modifiers)) {
            return false
        }
        return if (Modifier.isPublic(method.modifiers) || Modifier.isProtected(method.modifiers)) {
            true
        } else targetClass == null || getPackageName(method.declaringClass) == getPackageName(
            targetClass
        )
    }

}