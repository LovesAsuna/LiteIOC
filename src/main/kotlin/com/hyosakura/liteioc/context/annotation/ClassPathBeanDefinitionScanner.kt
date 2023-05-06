package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.BeanDefinitionStoreException
import com.hyosakura.liteioc.bean.factory.annotation.*
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.bean.factory.support.AbstractBeanDefinition
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionDefaults
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionReaderUtils.registerBeanDefinition
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.bean.factory.support.BeanNameGenerator
import com.hyosakura.liteioc.core.annotation.AnnotationConfigUtil
import com.hyosakura.liteioc.core.env.Environment
import com.hyosakura.liteioc.core.env.EnvironmentCapable
import com.hyosakura.liteioc.core.env.StandardEnvironment
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.*
import java.util.jar.JarFile

/**
 * @author LovesAsuna
 **/
class ClassPathBeanDefinitionScanner {

    companion object {

        val annotationTypes = arrayOf(Component::class.java)

        private fun getOrCreateEnvironment(registry: BeanDefinitionRegistry): Environment {
            return if (registry is EnvironmentCapable) {
                (registry as EnvironmentCapable).getEnvironment()
            } else StandardEnvironment()
        }

    }

    private val registry: BeanDefinitionRegistry

    private val beanDefinitionDefaults: BeanDefinitionDefaults = BeanDefinitionDefaults()

    private var environment: Environment? = null

    private var beanNameGenerator: BeanNameGenerator = AnnotationBeanNameGenerator.INSTANCE

    private var scopeMetadataResolver: ScopeMetadataResolver = AnnotationScopeMetadataResolver()

    private val logger = LoggerFactory.getLogger(javaClass)

    constructor(registry: BeanDefinitionRegistry) : this(registry, getOrCreateEnvironment(registry))

    constructor(
        registry: BeanDefinitionRegistry,
        environment: Environment,
    ) {
        this.registry = registry
        setEnvironment(environment)
    }

    fun setBeanNameGenerator(beanNameGenerator: BeanNameGenerator?) {
        this.beanNameGenerator = beanNameGenerator ?: AnnotationBeanNameGenerator.INSTANCE
    }

    fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    fun setScopedProxyMode(scopedProxyMode: ScopedProxyMode) {
        scopeMetadataResolver = AnnotationScopeMetadataResolver(scopedProxyMode)
    }

    fun setScopeMetadataResolver(scopeMetadataResolver: ScopeMetadataResolver?) {
        this.scopeMetadataResolver = scopeMetadataResolver ?: AnnotationScopeMetadataResolver()
    }

    fun getBeanDefinitionDefaults(): BeanDefinitionDefaults = beanDefinitionDefaults

    fun scan(vararg basePackages: String): Int {
        val beanCountAtScanStart = registry.getBeanDefinitionCount()

        doScan(*basePackages)

        return registry.getBeanDefinitionCount() - beanCountAtScanStart
    }

    fun doScan(vararg basePackages: String): Set<BeanDefinitionHolder> {
        val beanDefinitions = LinkedHashSet<BeanDefinitionHolder>()
        for (basePackage in basePackages) {
            // 在类路径下扫描获得components并将其转换成BeanDefinition
            val candidates: Set<BeanDefinition> = findCandidateComponents(basePackage)
            for (candidate in candidates) {
                // 解析scope属性
                val scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(candidate)
                candidate.setScope(scopeMetadata.getScopeName())
                val beanName = beanNameGenerator.generateBeanName(candidate, registry)
                if (candidate is AbstractBeanDefinition) {
                    postProcessBeanDefinition(candidate)
                }

                if (candidate is AnnotatedBeanDefinition) {
                    AnnotationConfigUtil.processCommonDefinitionAnnotations(candidate)
                }

                if (checkCandidate(beanName, candidate)) {
                    val definitionHolder = BeanDefinitionHolder(candidate, beanName)
                    beanDefinitions.add(definitionHolder)
                    // 将BeanDefinition注册到bean工厂中
                    registerBeanDefinition(definitionHolder, registry)
                }
            }
        }
        return beanDefinitions
    }

    @Throws(IllegalStateException::class)
    fun checkCandidate(beanName: String, beanDefinition: BeanDefinition): Boolean {
        if (!registry.containsBeanDefinition(beanName)) {
            return true
        }
        val existingDef = registry.getBeanDefinition(beanName)
        if (isCompatible(beanDefinition, existingDef)) {
            return false
        }
        throw IllegalStateException(
            "Annotation-specified bean name '" + beanName +
                    "' for bean class [" + beanDefinition.getBeanClassName() + "] conflicts with existing, " +
                    "non-compatible bean definition of same name and class [" + existingDef.getBeanClassName() + "]"
        )
    }

    fun isCompatible(newDefinition: BeanDefinition, existingDefinition: BeanDefinition): Boolean {
        return existingDefinition !is ScannedGenericBeanDefinition || newDefinition.getSource() != null && newDefinition.getSource()!! == existingDefinition.getSource() ||  // scanned same file twice
                newDefinition == existingDefinition // scanned equivalent class twice
    }

    fun postProcessBeanDefinition(beanDefinition: AbstractBeanDefinition) {
        beanDefinition.applyDefaults(beanDefinitionDefaults)
    }

    fun findCandidateComponents(basePackage: String): Set<BeanDefinition> {
        val candidates: MutableSet<BeanDefinition> = LinkedHashSet()
        try {
            val classes = getClasses(basePackage)
            val traceEnabled = logger.isTraceEnabled
            val debugEnabled = logger.isDebugEnabled
            for (clazz in classes) {
                if (traceEnabled) {
                    logger.trace("Scanning $clazz")
                }
                try {
                    val sbd = ScannedGenericBeanDefinition(clazz)
                    if (isCandidateComponent(sbd)) {
                        if (debugEnabled) {
                            logger.debug("Identified candidate component class: $clazz")
                        }
                        candidates.add(sbd)
                    } else {
                        if (debugEnabled) {
                            logger.debug("Ignored because not a concrete top-level class: $clazz")
                        }
                    }
                } catch (ex: Throwable) {
                    throw BeanDefinitionStoreException(
                        "Failed to read candidate component class: $clazz", ex
                    )
                }
            }
        } catch (ex: IOException) {
            throw BeanDefinitionStoreException("I/O failure during classpath scanning", ex)
        }
        return candidates
    }

    fun isCandidateComponent(beanDefinition: AnnotatedBeanDefinition): Boolean {
        val metadata = beanDefinition.getMetadata()
        var match = false
        for (type in annotationTypes) {
            if (metadata.isAnnotated(type.name)) {
                match = true
                break
            }
        }
        if (!match) {
            return false
        }
        return metadata.isIndependent() && (metadata.isConcrete())
    }

    private fun getClasses(
        packageName: String,
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
    ): Set<Class<*>> {
        var packageNameClone = packageName
        val classes: MutableSet<Class<*>> = HashSet()
        val recursive = true
        val packageDirName = packageNameClone.replace('.', '/')
        val dirs: Enumeration<URL>
        try {
            dirs = classLoader.getResources(packageDirName)
            while (dirs.hasMoreElements()) {
                val url = dirs.nextElement() as URL
                val protocol: String = url.protocol
                if ("file" == protocol) {
                    val filePath = URLDecoder.decode(url.file, "UTF-8")
                    findAndAddClassesInPackageByFile(packageNameClone, filePath, recursive, classes)
                } else if ("jar" == protocol) {
                    var jar: JarFile
                    try {
                        jar = (url.openConnection() as JarURLConnection).jarFile
                        val entries = jar.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            var name = entry.name
                            if (name[0] == '/') {
                                name = name.substring(1)
                            }
                            if (name.startsWith(packageDirName)) {
                                val idx = name.lastIndexOf('/')
                                if (idx != -1) {
                                    packageNameClone = name.substring(0, idx).replace('/', '.')
                                }
                                if (idx != -1 || recursive) {
                                    if (name.endsWith(".class") && !entry.isDirectory) {
                                        val className = name.substring(packageNameClone.length + 1, name.length - 6)
                                        try {
                                            classes.add(classLoader.loadClass("$packageNameClone.$className"))
                                        } catch (ignore: Throwable) {
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return classes
    }

    private fun findAndAddClassesInPackageByFile(
        packageName: String,
        packagePath: String,
        recursive: Boolean,
        classes: MutableSet<Class<*>>
    ) {
        val dir = File(packagePath)
        if (!dir.exists() || !dir.isDirectory) {
            return
        }
        val dirFiles = dir.listFiles { file -> (recursive && file.isDirectory || file.name.endsWith(".class")) }
        for (file in dirFiles) {
            if (file.isDirectory) {
                findAndAddClassesInPackageByFile(
                    packageName + "." + file.name,
                    file.absolutePath,
                    recursive,
                    classes
                )
            } else {
                val className = file.name.substring(0, file.name.length - 6)
                try {
                    classes.add(Thread.currentThread().contextClassLoader.loadClass("$packageName.$className"))
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

}