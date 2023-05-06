package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.aop.Hook
import com.hyosakura.liteioc.aop.HookHandler
import com.hyosakura.liteioc.bean.factory.BeanDefinitionStoreException
import com.hyosakura.liteioc.bean.factory.annotation.AnnotatedBeanDefinition
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.bean.factory.support.AbstractBeanDefinition
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.bean.factory.support.BeanNameGenerator
import com.hyosakura.liteioc.core.annotation.AnnotationConfigUtil
import com.hyosakura.liteioc.core.env.Environment
import com.hyosakura.liteioc.core.io.ResourceLoader
import com.hyosakura.liteioc.core.type.*
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.LinkedMultiValueMap
import com.hyosakura.liteioc.util.MultiValueMap
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.function.Predicate
import kotlin.reflect.KClass

/**
 * @author LovesAsuna
 **/
class ConfigurationClassParser(
    private val environment: Environment,
    private val resourceLoader: ResourceLoader,
    private val componentScanBeanNameGenerator: BeanNameGenerator,
    private val registry: BeanDefinitionRegistry
) {

    companion object {

        private val DEFAULT_EXCLUSION_FILTER =
            Predicate { className: String ->
                className.startsWith("java.lang.annotation.")
            }

    }

    private val configurationClasses: MutableMap<ConfigurationClass, ConfigurationClass> = LinkedHashMap()

    private val importStack = ImportStack()

    private val componentScanParser: ComponentScanAnnotationParser

    private val knownSuperclasses: MutableMap<String, ConfigurationClass> = HashMap()

    private val logger = LoggerFactory.getLogger(javaClass)

    private val objectSourceClass = SourceClass(Any::class.java)

    init {
        this.componentScanParser =
            ComponentScanAnnotationParser(environment, resourceLoader, componentScanBeanNameGenerator, registry)
    }

    fun getConfigurationClasses(): Set<ConfigurationClass> {
        return configurationClasses.keys
    }

    fun parse(configCandidates: Set<BeanDefinitionHolder>) {
        for (holder in configCandidates) {
            val bd = holder.getBeanDefinition()
            try {
                if (bd is AnnotatedBeanDefinition) {
                    parse(bd.getMetadata(), holder.getBeanName())
                } else if (bd is AbstractBeanDefinition && bd.hasBeanClass()) {
                    parse(bd.getBeanClass(), holder.getBeanName())
                } else {
                    parse(bd.getBeanClassName()!!, holder.getBeanName())
                }
            } catch (ex: BeanDefinitionStoreException) {
                throw ex
            } catch (ex: Throwable) {
                throw BeanDefinitionStoreException(
                    "Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex
                )
            }
        }
    }

    @Throws(IOException::class)
    fun parse(metadata: AnnotationMetadata, beanName: String) {
        processConfigurationClass(ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER)
    }

    @Throws(IOException::class)
    fun parse(clazz: Class<*>, beanName: String) {
        processConfigurationClass(ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER)
    }

    @Throws(IOException::class)
    fun parse(className: String, beanName: String) {
        processConfigurationClass(ConfigurationClass(Class.forName(className), beanName), DEFAULT_EXCLUSION_FILTER)
    }

    @Throws(IOException::class)
    fun processConfigurationClass(configClass: ConfigurationClass, filter: Predicate<String>) {
        // 处理Imported的情况
        // 就是当前这个注解类有没有被别的类import
        val existingClass = this.configurationClasses[configClass]
        if (existingClass != null) {
            if (configClass.isImported()) {
                if (existingClass.isImported()) {
                    existingClass.mergeImportedBy(configClass)
                }
                // Otherwise ignore new imported config class; existing non-imported class overrides it.
                return
            } else {
                // Explicit bean definition found, probably replacing an import.
                // Let's remove the old one and go with the new one.
                this.configurationClasses.remove(configClass)
                this.knownSuperclasses.values.removeIf(configClass::equals)
            }
        }

        var sourceClass: SourceClass? = asSourceClass(configClass, filter)
        do {
            sourceClass = doProcessConfigurationClass(configClass, sourceClass!!, filter)
        } while (sourceClass != null)

        this.configurationClasses[configClass] = configClass
    }

    @Throws(IOException::class)
    fun doProcessConfigurationClass(
        configClass: ConfigurationClass, sourceClass: SourceClass, filter: Predicate<String>
    ): SourceClass? {
        if (configClass.getMetadata().isAnnotated(Component::class.java.name)) {
            processMemberClasses(configClass, sourceClass, filter)
            processHook(configClass, sourceClass)
        }

        val componentScans = AnnotationConfigUtil.attributesForRepeatable(
            sourceClass.getMetadata(), ComponentScans::class.java, ComponentScan::class.java
        )

        if (componentScans.isNotEmpty()) {
            for (componentScan in componentScans) {
                val scannedBeanDefinitions =
                    this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName())

                for (holder in scannedBeanDefinitions) {
                    val bdCand = holder.getBeanDefinition()
                    if (ConfigurationClassUtil.checkConfigurationClassCandidate(bdCand)) {
                        parse(bdCand.getBeanClassName()!!, holder.getBeanName())
                    }
                }
            }
        }

        val beanMethods: Set<MethodMetadata> = retrieveBeanMethodMetadata(sourceClass)
        for (methodMetadata in beanMethods) {
            configClass.addBeanMethod(BeanMethod(methodMetadata, configClass))
        }

        // Process default methods on interfaces
        processInterfaces(configClass, sourceClass)

        // Process superclass, if any
        if (sourceClass.getMetadata().hasSuperClass()) {
            val superclass = sourceClass.getMetadata().getSuperClassName()
            if (superclass != null && !superclass.startsWith("java") &&
                !this.knownSuperclasses.containsKey(superclass)
            ) {
                this.knownSuperclasses[superclass] = configClass
                // Superclass found, return its annotation metadata and recurse
                return sourceClass.getSuperClass()
            }
        }

        // No superclass -> processing is complete
        return null
    }

    @Throws(IOException::class)
    private fun processInterfaces(configClass: ConfigurationClass, sourceClass: SourceClass) {
        for (ifc in sourceClass.getInterfaces()) {
            val beanMethods = retrieveBeanMethodMetadata(ifc)
            for (methodMetadata in beanMethods) {
                if (!methodMetadata.isAbstract()) {
                    // A default method or other concrete method on a Java 8+ interface...
                    configClass.addBeanMethod(BeanMethod(methodMetadata, configClass))
                }
            }
            processInterfaces(configClass, ifc)
        }
    }

    private fun retrieveBeanMethodMetadata(sourceClass: SourceClass): Set<MethodMetadata> {
        val original = sourceClass.getMetadata()
        var beanMethods = original.getAnnotatedMethods(Bean::class.java.name)
        if (beanMethods.size > 1 && original is StandardAnnotationMetadata) {
            try {
                val asm: AnnotationMetadata = StandardAnnotationMetadata(Class.forName(original.getClassName()))
                val asmMethods = asm.getAnnotatedMethods(Bean::class.java.name)
                if (asmMethods.size >= beanMethods.size) {
                    val selectedMethods: MutableSet<MethodMetadata> = LinkedHashSet(asmMethods.size)
                    for (asmMethod in asmMethods) {
                        for (beanMethod in beanMethods) {
                            if (beanMethod.getMethodName() == asmMethod.getMethodName()) {
                                selectedMethods.add(beanMethod)
                                break
                            }
                        }
                    }
                    if (selectedMethods.size == beanMethods.size) {
                        // All reflection-detected methods found in ASM method set -> proceed
                        beanMethods = selectedMethods
                    }
                }
            } catch (ex: IOException) {
                logger.debug("Failed to read class file via ASM for determining @Bean method order", ex)
                // No worries, let's continue with the reflection metadata we started with...
            }
        }
        return beanMethods
    }

    @Throws(IOException::class)
    private fun processMemberClasses(
        configClass: ConfigurationClass,
        sourceClass: SourceClass,
        filter: Predicate<String>
    ) {
        val memberClasses = sourceClass.getMemberClasses()
        if (!memberClasses.isEmpty()) {
            val candidates = ArrayList<SourceClass>(memberClasses.size)
            for (memberClass in memberClasses) {
                if (ConfigurationClassUtil.isConfigurationCandidate(memberClass.getMetadata()) &&
                    memberClass.getMetadata().getClassName() != configClass.getMetadata().getClassName()
                ) {
                    candidates.add(memberClass)
                }
            }
            for (candidate in candidates) {
                if (this.importStack.contains(configClass)) {
                    this.logger.error(
                        java.lang.String.format(
                            "A circular @Import has been detected: " +
                                    "Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
                                    "already present in the current import stack %s",
                            importStack.last().getSimpleName(),
                            configClass.getSimpleName(),
                            configClass.getSimpleName(),
                            importStack
                        )
                    )
                } else {
                    this.importStack.addLast(configClass)
                    try {
                        processConfigurationClass(candidate.asConfigClass(configClass), filter)
                    } finally {
                        this.importStack.removeLast()
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processHook(configurationClass: ConfigurationClass, sourceClass: SourceClass) {
        val meta = sourceClass.getMetadata()
        val hookMethods = meta.getAnnotatedMethods(Hook::class.java.name)
        for (method in hookMethods) {
            val attributes = method.getAnnotationAttributes(Hook::class.java.name)!!
            val handlerClasses = attributes["value"] as? Array<KClass<out HookHandler>> ?: continue
            for (handlerClass in handlerClasses) {
                this.registry.registerBeanDefinition(
                    handlerClass.qualifiedName!!,
                    ScannedGenericBeanDefinition(handlerClass.java)
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun asSourceClass(configurationClass: ConfigurationClass, filter: Predicate<String>): SourceClass {
        val metadata = configurationClass.getMetadata()
        return if (metadata is StandardAnnotationMetadata) {
            asSourceClass(metadata.getIntrospectedClass(), filter)
        } else asSourceClass(metadata.getClassName(), filter)
    }

    fun getImportRegistry(): ImportRegistry {
        return importStack
    }

    @Throws(IOException::class)
    fun asSourceClass(classType: Class<*>?, filter: Predicate<String>): SourceClass {
        return if (classType == null || filter.test(classType.name)) {
            this.objectSourceClass
        } else try {
            SourceClass(classType)
        } catch (ex: Throwable) {
            // Enforce ASM via class name resolution
            asSourceClass(classType.name, filter)
        }
    }

    @Throws(IOException::class)
    fun asSourceClass(className: String?, filter: Predicate<String>): SourceClass {
        if (className == null || filter.test(className)) {
            return this.objectSourceClass
        }
        return if (className.startsWith("java")) {
            // Never use ASM for core java types
            try {
                SourceClass(ClassUtil.forName(className, null))
            } catch (ex: ClassNotFoundException) {
                throw IOException("Failed to load class [$className]", ex)
            }
        } else SourceClass(Class.forName(className))
    }

    inner class SourceClass(private val source: Any) {

        private var metadata: AnnotationMetadata = AnnotationMetadata.introspect(source as Class<*>)

        @Throws(IOException::class)
        fun getMemberClasses(): Collection<SourceClass> {
            var sourceToProcess = this.source
            if (sourceToProcess is Class<*>) {
                sourceToProcess = try {
                    val declaredClasses = sourceToProcess.declaredClasses
                    val members = ArrayList<SourceClass>(declaredClasses.size)
                    for (declaredClass in declaredClasses) {
                        members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER))
                    }
                    return members
                } catch (err: NoClassDefFoundError) {
                    // getDeclaredClasses() failed because of non-resolvable dependencies
                    // -> fall back to ASM below
                    StandardClassMetadata(sourceToProcess)
                }
            }

            val sourceReader = sourceToProcess as ClassMetadata
            val memberClassNames: Array<String> = sourceReader.getMemberClassNames()
            val members = ArrayList<SourceClass>(memberClassNames.size)
            for (memberClassName in memberClassNames) {
                try {
                    members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER))
                } catch (ex: IOException) {
                    // Let's skip it if it's not resolvable - we're just looking for candidates
                    if (logger.isDebugEnabled) {
                        logger.debug(
                            "Failed to resolve member class [" + memberClassName +
                                    "] - not considering it as a configuration class candidate"
                        )
                    }
                }
            }
            return members
        }

        @Throws(IOException::class)
        fun getSuperClass(): SourceClass {
            return asSourceClass((source as Class<*>).superclass, DEFAULT_EXCLUSION_FILTER)
        }

        fun getMetadata(): AnnotationMetadata {
            return this.metadata
        }

        fun asConfigClass(importedBy: ConfigurationClass): ConfigurationClass {
            return ConfigurationClass(source as Class<*>, importedBy)
        }

        @Throws(IOException::class)
        fun getInterfaces(): Set<SourceClass> {
            val result: MutableSet<SourceClass> = LinkedHashSet()
            if (source is Class<*>) {
                for (ifcClass in source.interfaces) {
                    result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER))
                }
            } else {
                for (className in metadata.getInterfaceNames()) {
                    result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER))
                }
            }
            return result
        }

    }

    private class ImportStack : ArrayDeque<ConfigurationClass>(), ImportRegistry {

        private val imports: MultiValueMap<String, AnnotationMetadata> = LinkedMultiValueMap()

        fun registerImport(importingClass: AnnotationMetadata, importedClass: String) {
            imports.add(importedClass, importingClass)
        }

        override fun getImportingClassFor(importedClass: String): AnnotationMetadata? {
            return imports[importedClass]?.lastOrNull()
        }

        override fun removeImportingClass(importingClass: String) {
            val values = imports.values
            for (list in imports.values) {
                val iterator = list.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next()!!.getClassName() == importingClass) {
                        iterator.remove()
                        break
                    }
                }
            }
        }

        override fun toString(): String {
            val joiner = StringJoiner("->", "[", "]")
            for (configurationClass in this) {
                joiner.add(configurationClass.getSimpleName())
            }
            return joiner.toString()
        }
    }

}