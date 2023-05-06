package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.*
import com.hyosakura.liteioc.bean.factory.*
import com.hyosakura.liteioc.bean.factory.config.AutowireCapableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.ConstructorArgumentValues
import com.hyosakura.liteioc.bean.factory.config.DependencyDescriptor
import com.hyosakura.liteioc.core.CollectionFactory
import com.hyosakura.liteioc.core.MethodParameter
import com.hyosakura.liteioc.core.NamedThreadLocal
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.MethodInvoker
import com.hyosakura.liteioc.util.ObjectUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import org.slf4j.Logger
import java.beans.ConstructorProperties
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

/**
 * @author LovesAsuna
 **/
class ConstructorResolver {

    companion object {

        private val EMPTY_ARGS = arrayOfNulls<Any>(0)

        private val currentInjectionPoint: NamedThreadLocal<InjectionPoint> =
            NamedThreadLocal("Current injection point")

        private val autowiredArgumentMarker = Any()

        fun setCurrentInjectionPoint(injectionPoint: InjectionPoint?): InjectionPoint? {
            val old = currentInjectionPoint.get()
            if (injectionPoint != null) {
                currentInjectionPoint.set(injectionPoint)
            } else {
                currentInjectionPoint.remove()
            }
            return old
        }

    }

    private val beanFactory: AbstractAutowireCapableBeanFactory

    private val logger: Logger

    constructor(beanFactory: AbstractAutowireCapableBeanFactory) {
        this.beanFactory = beanFactory
        this.logger = beanFactory.logger
    }

    fun autowireConstructor(
        beanName: String,
        mbd: RootBeanDefinition,
        chosenCtors: Array<Constructor<*>>?,
        explicitArgs: Array<out Any?>?
    ): BeanWrapper {
        val bw = BeanWrapperImpl()

        var constructorToUse: Constructor<*>? = null
        var argsHolderToUse: ArgumentsHolder? = null
        var argsToUse: Array<out Any?>? = null

        if (explicitArgs != null) {
            argsToUse = explicitArgs
        } else {
            var argsToResolve: Array<out Any?>? = null
            synchronized(mbd.constructorArgumentLock) {
                constructorToUse = mbd.resolvedConstructor as Constructor<*>?
                if (constructorToUse != null && mbd.constructorArgumentsResolved) {
                    // Found a cached constructor...
                    argsToUse = mbd.resolvedConstructorArguments
                    if (argsToUse == null) {
                        argsToResolve = mbd.preparedConstructorArguments
                    }
                }
            }
            if (argsToResolve != null) {
                argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve)
            }
        }
        if (constructorToUse == null || argsToUse == null) {
            // Take specified constructors, if any.
            var candidates = chosenCtors
            if (candidates == null) {
                val beanClass = mbd.getBeanClass()
                try {
                    candidates =
                        if (mbd.isNonPublicAccessAllowed()) beanClass.declaredConstructors else beanClass.constructors
                } catch (ex: Throwable) {
                    throw BeanCreationException(
                        beanName,
                        "Resolution of declared constructors on bean Class [" + beanClass.name +
                                "] from ClassLoader [" + beanClass.classLoader + "] failed", ex
                    )
                }
            }

            if ((candidates!!.size == 1) && (explicitArgs == null) && !mbd.hasConstructorArgumentValues()) {
                val uniqueCandidate = candidates[0]
                if (uniqueCandidate.parameterCount == 0) {
                    synchronized(mbd.constructorArgumentLock) {
                        mbd.resolvedConstructor = uniqueCandidate
                        mbd.constructorArgumentsResolved = true
                        mbd.resolvedConstructorArguments = EMPTY_ARGS
                    }
                    bw.setBeanInstance(instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS))
                    return bw
                }
            }

            // Need to resolve the constructor.
            val autowiring =
                (chosenCtors != null || mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)
            var resolvedValues: ConstructorArgumentValues? = null

            val minNrOfArgs: Int
            if (explicitArgs != null) {
                minNrOfArgs = explicitArgs.size
            } else {
                val cargs = mbd.getConstructorArgumentValues()
                resolvedValues = ConstructorArgumentValues()
                minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues)
            }

            AutowireUtil.sortConstructors(candidates)
            // 定义了一个差异变量，这个变量很有分量，后面有注释
            var minTypeDiffWeight = Int.MAX_VALUE
            // 记录异常的构造方法（当构造方法差异值一样时，Spring不知如何选择）
            var ambiguousConstructors: MutableSet<Constructor<*>?>? = null
            var causes: Deque<UnsatisfiedDependencyException>? = null

            // 遍历所有的候选构造方法
            for (candidate in candidates) {
                val parameterCount = candidate.parameterCount

                if ((constructorToUse != null) && (argsToUse != null) && (argsToUse!!.size > parameterCount)) {
                    // Already found greedy constructor that can be satisfied ->
                    // do not look any further, there are only less greedy constructors left.
                    break
                }

                if (parameterCount < minNrOfArgs) {
                    continue
                }
                var argsHolder: ArgumentsHolder
                val paramTypes = candidate.parameterTypes
                if (resolvedValues != null) {
                    try {
                        var paramNames = ConstructorPropertiesChecker.evaluate(candidate, parameterCount)
                        if (paramNames == null) {
                            val pnd = beanFactory.getParameterNameDiscoverer()
                            if (pnd != null) {
                                // 获取构造方法参数名称列表
                                paramNames = pnd.getParameterNames(candidate)
                            }
                        }
                        argsHolder = createArgumentArray(
                            beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
                            getUserDeclaredConstructor(candidate), autowiring, candidates.size == 1
                        )
                    } catch (ex: UnsatisfiedDependencyException) {
                        if (logger.isTraceEnabled) {
                            logger.trace("Ignoring constructor [$candidate] of bean '$beanName': $ex")
                        }
                        // Swallow and try next constructor.
                        if (causes == null) {
                            causes = ArrayDeque(1)
                        }
                        causes.add(ex)
                        continue
                    }
                } else {
                    // Explicit arguments given -> arguments length must match exactly.
                    if (parameterCount != explicitArgs!!.size) {
                        continue
                    }
                    argsHolder = ArgumentsHolder(explicitArgs as Array<Any?>)
                }

                val typeDiffWeight: Int =
                    (if (mbd.isLenientConstructorResolution()) argsHolder.getTypeDifferenceWeight(paramTypes) else argsHolder.getAssignabilityWeight(
                        paramTypes
                    ))
                // Choose this constructor if it represents the closest match.
                if (typeDiffWeight < minTypeDiffWeight) {
                    constructorToUse = candidate
                    argsHolderToUse = argsHolder
                    argsToUse = argsHolder.arguments
                    minTypeDiffWeight = typeDiffWeight
                    ambiguousConstructors = null
                } else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
                    if (ambiguousConstructors == null) {
                        ambiguousConstructors = LinkedHashSet()
                        ambiguousConstructors.add(constructorToUse)
                    }
                    ambiguousConstructors.add(candidate)
                }
            }

            if (constructorToUse == null) {
                if (causes != null) {
                    val ex = causes.removeLast()
                    for (cause in causes) {
                        beanFactory.onSuppressedException(cause)
                    }
                    throw ex!!
                }
                throw BeanCreationException(
                    beanName,
                    (("Could not resolve matching constructor on bean class [" + mbd.getBeanClassName()) + "] " +
                            "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)")
                )
            } else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
                throw BeanCreationException(
                    beanName,
                    (("Ambiguous constructor matches found on bean class [" + mbd.getBeanClassName()) + "] " +
                            "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
                            ambiguousConstructors)
                )
            }

            if (explicitArgs == null && argsHolderToUse != null) {
                argsHolderToUse.storeCache(mbd, constructorToUse!!)
            }
        }
        requireNotNull(argsToUse) { "Unresolved constructor arguments" }
        bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse!!, argsToUse!!))
        return bw
    }

    @Throws(UnsatisfiedDependencyException::class)
    private fun createArgumentArray(
        beanName: String, mbd: RootBeanDefinition, resolvedValues: ConstructorArgumentValues?,
        bw: BeanWrapper, paramTypes: Array<Class<*>>, paramNames: Array<String>?, executable: Executable,
        autowiring: Boolean, fallback: Boolean
    ): ArgumentsHolder {
        val customConverter = beanFactory.getCustomTypeConverter()
        val converter = customConverter ?: bw
        val args = ArgumentsHolder(paramTypes.size)
        val usedValueHolders = HashSet<ConstructorArgumentValues.ValueHolder>(paramTypes.size)
        val autowiredBeanNames = LinkedHashSet<String>(4)
        for (paramIndex in paramTypes.indices) {
            val paramType = paramTypes[paramIndex]
            val paramName = paramNames?.get(paramIndex) ?: ""
            // Try to find matching constructor argument value, either indexed or generic.
            var valueHolder: ConstructorArgumentValues.ValueHolder? = null
            if (resolvedValues != null) {
                valueHolder = resolvedValues.getArgumentValue(paramIndex, paramType, paramName, usedValueHolders)
                // If we couldn't find a direct match and are not supposed to autowire,
                // let's try the next generic, untyped argument value as fallback:
                // it could match after type conversion (for example, String -> int).
                if (valueHolder == null && (!autowiring || paramTypes.size == resolvedValues.getArgumentCount())) {
                    valueHolder = resolvedValues.getGenericArgumentValue(null, null, usedValueHolders)
                }
            }
            if (valueHolder != null) {
                // We found a potential match - let's give it a try.
                // Do not consider the same value definition multiple times!
                usedValueHolders.add(valueHolder)
                val originalValue = valueHolder.value
                var convertedValue: Any?
                if (valueHolder.isConverted) {
                    convertedValue = valueHolder.convertedValue
                    args.preparedArguments[paramIndex] = convertedValue
                } else {
                    val methodParam = MethodParameter.forExecutable(executable, paramIndex)
                    try {
                        convertedValue = converter.convertIfNecessary(originalValue, paramType)
                    } catch (ex: TypeMismatchException) {
                        throw UnsatisfiedDependencyException(
                            beanName, InjectionPoint(methodParam),
                            ("Could not convert argument value of type [" +
                                    ObjectUtil.nullSafeClassName(valueHolder.value)) +
                                    "] to required type [" + paramType.name + "]: " + ex.message
                        )
                    }
                    val sourceHolder = valueHolder.source
                    if (sourceHolder is ConstructorArgumentValues.ValueHolder) {
                        val sourceValue = sourceHolder.value
                        args.resolveNecessary = true
                        args.preparedArguments[paramIndex] = sourceValue
                    }
                }
                args.arguments[paramIndex] = convertedValue
                args.rawArguments[paramIndex] = originalValue
            } else {
                val methodParam: MethodParameter = MethodParameter.forExecutable(executable, paramIndex)
                // No explicit match found: we're either supposed to autowire or
                // have to fail creating an argument array for the given constructor.
                if (!autowiring) {
                    throw UnsatisfiedDependencyException(
                        beanName, InjectionPoint(methodParam),
                        "Ambiguous argument values for parameter of type [" + paramType.name +
                                "] - did you specify the correct bean references as arguments?"
                    )
                }
                try {
                    val autowiredArgument = resolveAutowiredArgument(
                        methodParam, beanName, autowiredBeanNames, converter, fallback
                    )
                    args.rawArguments[paramIndex] = autowiredArgument
                    args.arguments[paramIndex] = autowiredArgument
                    args.preparedArguments[paramIndex] = autowiredArgumentMarker
                    args.resolveNecessary = true
                } catch (ex: BeansException) {
                    throw UnsatisfiedDependencyException(beanName, InjectionPoint(methodParam), ex)
                }
            }
        }
        for (autowiredBeanName: String in autowiredBeanNames) {
            beanFactory.registerDependentBean(autowiredBeanName, beanName)
            if (logger.isDebugEnabled) {
                logger.debug(
                    ("Autowiring by type from bean name '" + beanName +
                            "' via " + (if (executable is Constructor<*>) "constructor" else "factory method") +
                            " to bean named '" + autowiredBeanName + "'")
                )
            }
        }
        return args
    }

    open fun getUserDeclaredConstructor(constructor: Constructor<*>): Constructor<*> {
        val declaringClass = constructor.declaringClass
        val userClass = ClassUtil.getUserClass(declaringClass)
        if (userClass != declaringClass) {
            try {
                return userClass.getDeclaredConstructor(*constructor.parameterTypes)
            } catch (ex: NoSuchMethodException) {
                // No equivalent constructor on user class (superclass)...
                // Let's proceed with the given constructor as we usually would.
            }
        }
        return constructor
    }

    fun resolveAutowiredArgument(
        param: MethodParameter, beanName: String?,
        autowiredBeanNames: MutableSet<String>?, typeConverter: TypeConverter?, fallback: Boolean
    ): Any? {
        val paramType = param.getParameterType()
        return if (InjectionPoint::class.java.isAssignableFrom(paramType)) {
            currentInjectionPoint.get()
                ?: throw IllegalStateException("No current InjectionPoint available for $param")
        } else try {
            beanFactory.resolveDependency(
                DependencyDescriptor(param, true), beanName, autowiredBeanNames, typeConverter
            )
        } catch (ex: NoUniqueBeanDefinitionException) {
            throw ex
        } catch (ex: NoSuchBeanDefinitionException) {
            if (fallback) {
                // Single constructor or factory method -> let's return an empty array/collection
                // for e.g. a vararg or a non-null List/Set/Map parameter.
                if (paramType.isArray) {
                    return java.lang.reflect.Array.newInstance(paramType.componentType, 0)
                } else if (CollectionFactory.isApproximableCollectionType(paramType)) {
                    return CollectionFactory.createCollection<Any>(paramType, 0)
                } else if (CollectionFactory.isApproximableMapType(paramType)) {
                    return CollectionFactory.createMap<Any, Any>(paramType, 0)
                }
            }
            throw ex
        }
    }

    private fun resolvePreparedArguments(
        beanName: String,
        mbd: RootBeanDefinition,
        bw: BeanWrapper,
        executable: Executable?,
        argsToResolve: Array<out Any?>?
    ): Array<out Any?> {
        // todo resolve
        return argsToResolve!!
    }

    private fun getCandidateMethods(factoryClass: Class<*>, mbd: RootBeanDefinition): Array<Method> {
        return if (mbd.isNonPublicAccessAllowed()) ReflectionUtil.getAllDeclaredMethods(factoryClass) else factoryClass.methods
    }

    fun instantiateUsingFactoryMethod(
        beanName: String, mbd: RootBeanDefinition, explicitArgs: Array<Any?>?
    ): BeanWrapper {
        val bw = BeanWrapperImpl()
        var factoryBean: Any?
        var factoryClass: Class<*>
        val isStatic: Boolean

        val factoryBeanName = mbd.getFactoryBeanName()
        if (factoryBeanName != null) {
            if (factoryBeanName == beanName) {
                throw BeanDefinitionStoreException(
                    beanName,
                    "factory-bean reference points back to the same bean definition"
                )
            }
            factoryBean = this.beanFactory.getBean(factoryBeanName)
            if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {
                throw ImplicitlyAppearedSingletonException()
            }
            this.beanFactory.registerDependentBean(factoryBeanName, beanName)
            factoryClass = factoryBean.javaClass
            isStatic = false
        } else {
            // It's a static factory method on the bean class.
            if (!mbd.hasBeanClass()) {
                throw BeanDefinitionStoreException(
                    beanName,
                    "bean definition declares neither a bean class nor a factory-bean reference"
                )
            }
            factoryBean = null
            factoryClass = mbd.getBeanClass()
            isStatic = true
        }

        var factoryMethodToUse: Method? = null
        var argsHolderToUse: ArgumentsHolder? = null
        var argsToUse: Array<out Any?>? = null
        if (explicitArgs != null) {
            argsToUse = explicitArgs
        } else {
            var argsToResolve: Array<out Any?>? = null
            synchronized(mbd.constructorArgumentLock) {
                factoryMethodToUse = mbd.resolvedConstructorOrFactoryMethod as Method?
                if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {
                    // Found a cached factory method...
                    argsToUse = mbd.resolvedConstructorArguments
                    if (argsToUse == null) {
                        argsToResolve = mbd.preparedConstructorArguments
                    }
                }
            }
            if (argsToResolve != null) {
                argsToUse = resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve)
            }
        }
        if (factoryMethodToUse == null || argsToUse == null) {
            // Need to determine the factory method...
            // Try all methods with this name to see if they match the given arguments.
            factoryClass = ClassUtil.getUserClass(factoryClass)
            var candidates: MutableList<Method>? = null
            if (mbd.isFactoryMethodUnique) {
                if (factoryMethodToUse == null) {
                    factoryMethodToUse = mbd.getResolvedFactoryMethod()
                }
                if (factoryMethodToUse != null) {
                    candidates = mutableListOf(factoryMethodToUse!!)
                }
            }
            if (candidates == null) {
                candidates = ArrayList()
                val rawCandidates = getCandidateMethods(factoryClass, mbd)
                for (candidate in rawCandidates) {
                    if (Modifier.isStatic(candidate.modifiers) == isStatic && mbd.isFactoryMethod(candidate)) {
                        candidates.add(candidate)
                    }
                }
            }
            if (candidates.size == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
                val uniqueCandidate = candidates[0]
                if (uniqueCandidate.parameterCount == 0) {
                    mbd.factoryMethodToIntrospect = uniqueCandidate
                    synchronized(mbd.constructorArgumentLock) {
                        mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate
                        mbd.constructorArgumentsResolved = true
                        mbd.resolvedConstructorArguments = EMPTY_ARGS
                    }
                    bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, uniqueCandidate, emptyArray()))
                    return bw
                }
            }
            if (candidates.size > 1) {  // explicitly skip immutable singletonList
                candidates.sortedWith(AutowireUtil.EXECUTABLE_COMPARATOR)
            }
            var resolvedValues: ConstructorArgumentValues? = null
            val autowiring = mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR
            var minTypeDiffWeight = Int.MAX_VALUE
            var ambiguousFactoryMethods: MutableSet<Method?>? = null
            val minNrOfArgs: Int
            if (explicitArgs != null) {
                minNrOfArgs = explicitArgs.size
            } else {
                // We don't have arguments passed in programmatically, so we need to resolve the
                // arguments specified in the constructor arguments held in the bean definition.
                if (mbd.hasConstructorArgumentValues()) {
                    val cargs = mbd.getConstructorArgumentValues()
                    resolvedValues = ConstructorArgumentValues()
                    minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues)
                } else {
                    minNrOfArgs = 0
                }
            }
            var causes: Deque<UnsatisfiedDependencyException?>? = null
            for (candidate: Method in candidates) {
                val parameterCount = candidate.parameterCount
                if (parameterCount >= minNrOfArgs) {
                    var argsHolder: ArgumentsHolder
                    val paramTypes = candidate.parameterTypes
                    if (explicitArgs != null) {
                        // Explicit arguments given -> arguments length must match exactly.
                        if (paramTypes.size != explicitArgs.size) {
                            continue
                        }
                        argsHolder = ArgumentsHolder(explicitArgs)
                    } else {
                        // Resolved constructor arguments: type conversion and/or autowiring necessary.
                        try {
                            var paramNames: Array<String>? = null
                            val pnd = beanFactory.getParameterNameDiscoverer()
                            if (pnd != null) {
                                paramNames = pnd.getParameterNames(candidate)
                            }
                            argsHolder = createArgumentArray(
                                beanName, mbd, resolvedValues, bw,
                                paramTypes, paramNames, candidate, autowiring, candidates.size == 1
                            )
                        } catch (ex: UnsatisfiedDependencyException) {
                            if (logger.isTraceEnabled) {
                                logger.trace("Ignoring factory method [$candidate] of bean '$beanName': $ex")
                            }
                            // Swallow and try next overloaded factory method.
                            if (causes == null) {
                                causes = ArrayDeque(1)
                            }
                            causes.add(ex)
                            continue
                        }
                    }
                    val typeDiffWeight =
                        if (mbd.isLenientConstructorResolution()) argsHolder.getTypeDifferenceWeight(paramTypes) else argsHolder.getAssignabilityWeight(
                            paramTypes
                        )
                    // Choose this factory method if it represents the closest match.
                    if (typeDiffWeight < minTypeDiffWeight) {
                        factoryMethodToUse = candidate
                        argsHolderToUse = argsHolder
                        argsToUse = argsHolder.arguments
                        minTypeDiffWeight = typeDiffWeight
                        ambiguousFactoryMethods = null
                    } else if (factoryMethodToUse != null && typeDiffWeight == minTypeDiffWeight &&
                        !mbd.isLenientConstructorResolution() && paramTypes.size == factoryMethodToUse!!.parameterCount &&
                        !Arrays.equals(paramTypes, factoryMethodToUse!!.parameterTypes)
                    ) {
                        if (ambiguousFactoryMethods == null) {
                            ambiguousFactoryMethods = LinkedHashSet()
                            ambiguousFactoryMethods.add(factoryMethodToUse)
                        }
                        ambiguousFactoryMethods.add(candidate)
                    }
                }
            }
            if (factoryMethodToUse == null || argsToUse == null) {
                if (causes != null) {
                    val ex = causes.removeLast()
                    for (cause: Exception? in causes) {
                        beanFactory.onSuppressedException(cause!!)
                    }
                    throw ex!!
                }
                val argTypes: MutableList<String> = ArrayList(minNrOfArgs)
                if (explicitArgs != null) {
                    for (arg: Any? in explicitArgs) {
                        argTypes.add(if (arg != null) arg.javaClass.simpleName else "null")
                    }
                } else if (resolvedValues != null) {
                    val valueHolders =
                        LinkedHashSet<ConstructorArgumentValues.ValueHolder>(resolvedValues.getArgumentCount())
                    valueHolders.addAll(resolvedValues.getIndexedArgumentValues().values)
                    valueHolders.addAll(resolvedValues.getGenericArgumentValues())
                    for (value in valueHolders) {
                        val argType =
                            if (value.type != null) ClassUtil.getShortName(value.type!!) else if (value.value != null) value.value!!
                                .javaClass.simpleName else "null"
                        argTypes.add(argType)
                    }
                }
                val argDesc = argTypes.joinToString(",")
                throw BeanCreationException(
                     beanName,
                    ("No matching factory method found on class [" + factoryClass.name + "]: " +
                            (if (mbd.getFactoryBeanName() != null) ("factory bean '" + mbd.getFactoryBeanName()) + "'; " else "") +
                            "factory method '" + mbd.getFactoryMethodName()) + "(" + argDesc + ")'. " +
                            "Check that a method with the specified name " +
                            (if (minNrOfArgs > 0) "and arguments " else "") +
                            "exists and that it is " +
                            (if (isStatic) "static" else "non-static") + "."
                )
            } else if (Void.TYPE == factoryMethodToUse!!.returnType) {
                throw BeanCreationException(
                    beanName,
                    (("Invalid factory method '" + mbd.getFactoryMethodName()) + "' on class [" +
                            factoryClass.name + "]: needs to have a non-void return type!")
                )
            } else if (ambiguousFactoryMethods != null) {
                throw BeanCreationException(
                    beanName,
                    ("Ambiguous factory method matches found on class [" + factoryClass.name + "] " +
                            "(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
                            ambiguousFactoryMethods)
                )
            }
            if (explicitArgs == null && argsHolderToUse != null) {
                mbd.factoryMethodToIntrospect = factoryMethodToUse
                argsHolderToUse.storeCache(mbd, factoryMethodToUse!!)
            }
        }
        bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, factoryMethodToUse!!, argsToUse!!))
        return bw
    }

    private fun instantiate(
        beanName: String, mbd: RootBeanDefinition, constructorToUse: Constructor<*>, argsToUse: Array<out Any?>
    ): Any {
        return try {
            val strategy = this.beanFactory.getInstantiationStrategy()
            strategy.instantiate(mbd, beanName, beanFactory, constructorToUse, *argsToUse)
        } catch (ex: Throwable) {
            throw BeanCreationException(beanName, "Bean instantiation via constructor failed", ex)
        }
    }

    private fun instantiate(
        beanName: String, mbd: RootBeanDefinition,
        factoryBean: Any?, factoryMethod: Method, args: Array<out Any?>
    ): Any {
        return try {
            this.beanFactory.getInstantiationStrategy().instantiate(
                mbd, beanName, beanFactory, factoryBean, factoryMethod, *args
            )
        } catch (ex: Throwable) {
            throw BeanCreationException(
                beanName,
                "Bean instantiation via factory method failed", ex
            )
        }
    }

    private fun resolveConstructorArguments(
        beanName: String, mbd: RootBeanDefinition, bw: BeanWrapper,
        cargs: ConstructorArgumentValues, resolvedValues: ConstructorArgumentValues
    ): Int {
        val converter: TypeConverter = beanFactory.getCustomTypeConverter() ?: bw
        val valueResolver = BeanDefinitionValueResolver(beanFactory, beanName, mbd, converter)
        var minNrOfArgs = cargs.getArgumentCount()
        for ((index, valueHolder) in cargs.getIndexedArgumentValues().entries) {
            if (index < 0) {
                throw BeanCreationException(beanName, "Invalid constructor argument index: $index")
            }
            if (index + 1 > minNrOfArgs) {
                minNrOfArgs = index + 1
            }
            if (valueHolder.isConverted) {
                resolvedValues.addIndexedArgumentValue(index, valueHolder)
            } else {
                val resolvedValue = valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.value)
                val resolvedValueHolder =
                    ConstructorArgumentValues.ValueHolder(resolvedValue, valueHolder.type, valueHolder.name)
                resolvedValueHolder.source = valueHolder
                resolvedValues.addIndexedArgumentValue(index, resolvedValueHolder)
            }
        }
        for (valueHolder in cargs.getGenericArgumentValues()) {
            if (valueHolder.isConverted) {
                resolvedValues.addGenericArgumentValue(valueHolder)
            } else {
                val resolvedValue = valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.value)
                val resolvedValueHolder =
                    ConstructorArgumentValues.ValueHolder(resolvedValue, valueHolder.type, valueHolder.name)
                resolvedValueHolder.source = valueHolder
                resolvedValues.addGenericArgumentValue(resolvedValueHolder)
            }
        }
        return minNrOfArgs
    }

    internal class ArgumentsHolder {
        val rawArguments: Array<Any?>
        val arguments: Array<Any?>
        val preparedArguments: Array<Any?>
        var resolveNecessary = false

        constructor(size: Int) {
            this.rawArguments = arrayOfNulls(size)
            this.arguments = arrayOfNulls(size)
            this.preparedArguments = arrayOfNulls(size)
        }

        constructor(args: Array<Any?>) {
            this.rawArguments = args
            this.arguments = args
            this.preparedArguments = args
        }

        fun getTypeDifferenceWeight(paramTypes: Array<Class<*>?>): Int {
            // If valid arguments found, determine type difference weight.
            // Try type difference weight on both the converted arguments and
            // the raw arguments. If the raw weight is better, use it.
            // Decrease raw weight by 1024 to prefer it over equal converted weight.
            val typeDiffWeight: Int = MethodInvoker.getTypeDifferenceWeight(paramTypes, arguments)
            val rawTypeDiffWeight: Int = MethodInvoker.getTypeDifferenceWeight(paramTypes, rawArguments) - 1024
            return rawTypeDiffWeight.coerceAtMost(typeDiffWeight)
        }

        fun getAssignabilityWeight(paramTypes: Array<Class<*>>): Int {
            for (i in paramTypes.indices) {
                if (!ClassUtil.isAssignableValue(paramTypes[i], arguments[i])) {
                    return Int.MAX_VALUE
                }
            }
            for (i in paramTypes.indices) {
                if (!ClassUtil.isAssignableValue(paramTypes[i], rawArguments[i])) {
                    return Int.MAX_VALUE - 512
                }
            }
            return Int.MAX_VALUE - 1024
        }

        fun storeCache(mbd: RootBeanDefinition, constructorOrFactoryMethod: Executable) {
            synchronized(mbd.constructorArgumentLock) {
                mbd.resolvedConstructor = constructorOrFactoryMethod
                mbd.constructorArgumentsResolved = true
                if (resolveNecessary) {
                    mbd.preparedConstructorArguments = preparedArguments
                } else {
                    mbd.resolvedConstructorArguments = arguments
                }
            }
        }
    }

    private object ConstructorPropertiesChecker {

        fun evaluate(candidate: Constructor<*>, paramCount: Int): Array<String>? {
            val cp = candidate.getAnnotation(ConstructorProperties::class.java)
            return if (cp != null) {
                val names = cp.value
                check(names.size == paramCount) {
                    "Constructor annotated with @ConstructorProperties but not " +
                            "corresponding to actual number of parameters (" + paramCount + "): " + candidate
                }
                names
            } else {
                null
            }
        }

    }

}