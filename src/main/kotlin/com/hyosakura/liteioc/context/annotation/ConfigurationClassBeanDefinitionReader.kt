package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.factory.BeanDefinitionStoreException
import com.hyosakura.liteioc.bean.factory.annotation.AnnotatedBeanDefinition
import com.hyosakura.liteioc.bean.factory.annotation.AnnotatedGenericBeanDefinition
import com.hyosakura.liteioc.bean.factory.annotation.AnnotationScopeMetadataResolver
import com.hyosakura.liteioc.bean.factory.annotation.ScopedProxyMode
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.bean.factory.parsing.SourceExtractor
import com.hyosakura.liteioc.bean.factory.support.*
import com.hyosakura.liteioc.core.annotation.AnnotationConfigUtil
import com.hyosakura.liteioc.core.env.Environment
import com.hyosakura.liteioc.core.io.ResourceLoader
import com.hyosakura.liteioc.core.type.AnnotationMetadata
import com.hyosakura.liteioc.core.type.MethodMetadata
import com.hyosakura.liteioc.core.type.StandardAnnotationMetadata
import com.hyosakura.liteioc.core.type.StandardMethodMetadata
import org.slf4j.LoggerFactory

/**
 * @author LovesAsuna
 **/
class ConfigurationClassBeanDefinitionReader {

    private val logger = LoggerFactory.getLogger(ConfigurationClassBeanDefinitionReader::class.java)

    private val scopeMetadataResolver: ScopeMetadataResolver = AnnotationScopeMetadataResolver()

    private val registry: BeanDefinitionRegistry

    private val sourceExtractor: SourceExtractor

    private val resourceLoader: ResourceLoader

    private val environment: Environment

    private val importBeanNameGenerator: BeanNameGenerator

    private val importRegistry: ImportRegistry

    constructor(
        registry: BeanDefinitionRegistry, sourceExtractor: SourceExtractor,
        resourceLoader: ResourceLoader, environment: Environment, importBeanNameGenerator: BeanNameGenerator,
        importRegistry: ImportRegistry
    ) {
        this.registry = registry
        this.sourceExtractor = sourceExtractor
        this.resourceLoader = resourceLoader
        this.environment = environment
        this.importBeanNameGenerator = importBeanNameGenerator
        this.importRegistry = importRegistry
    }

    fun loadBeanDefinitions(configurationModel: Set<ConfigurationClass>) {
        for (configClass in configurationModel) {
            loadBeanDefinitionsForConfigurationClass(configClass)
        }
    }

    private fun loadBeanDefinitionsForConfigurationClass(
        configClass: ConfigurationClass,
    ) {
        if (configClass.isImported()) {
            registerBeanDefinitionForImportedConfigurationClass(configClass)
        }
        // @Bean方法注册的bean，可能会覆盖掉通过ComponentScan注册的bean
        for (beanMethod in configClass.getBeanMethods()) {
            loadBeanDefinitionsForBeanMethod(beanMethod)
        }

    }

    private fun registerBeanDefinitionForImportedConfigurationClass(configClass: ConfigurationClass) {
        val metadata = configClass.getMetadata()
        val configBeanDef = AnnotatedGenericBeanDefinition(metadata)
        val scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(configBeanDef)
        configBeanDef.setScope(scopeMetadata.getScopeName())
        val configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry)
        AnnotationConfigUtil.processCommonDefinitionAnnotations(configBeanDef, metadata)
        val definitionHolder = BeanDefinitionHolder(configBeanDef, configBeanName)
//        definitionHolder = AnnotationConfigUtil.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry)
        this.registry.registerBeanDefinition(definitionHolder.getBeanName(), definitionHolder.getBeanDefinition())
        configClass.setBeanName(configBeanName)
        if (logger.isTraceEnabled) {
            logger.trace("Registered bean definition for imported class '$configBeanName'")
        }
    }

    private fun loadBeanDefinitionsForBeanMethod(beanMethod: BeanMethod) {
        val configClass = beanMethod.getConfigurationClass()
        val metadata = beanMethod.getMetadata()
        val methodName = metadata.getMethodName()

        val bean = AnnotationConfigUtil.attributesFor(metadata, Bean::class.java)
        requireNotNull(bean) { "No @Bean annotation attributes" }

        // Consider name and any aliases
        val names = mutableListOf(*bean.getStringArray("name"))
        val beanName = if (names.isNotEmpty()) names.removeAt(0) else methodName


        // Has this effectively been overridden before (e.g. via XML)?
        if (isOverriddenByExistingDefinition(beanMethod, beanName)) {
            if (beanName == beanMethod.getConfigurationClass().getBeanName()) {
                throw BeanDefinitionStoreException(
                    beanName, "Bean name derived from @Bean method '" + beanMethod.getMetadata().getMethodName() +
                            "' clashes with bean name for containing configuration class; please make those names unique!"
                )
            }
            return
        }
        val beanDef = ConfigurationClassBeanDefinition(configClass, metadata, beanName)
        beanDef.setSource(sourceExtractor.extractSource(metadata, configClass.getResource()))
        if (metadata.isStatic()) {
            // static @Bean method
            val sam = configClass.getMetadata() as? StandardAnnotationMetadata
            if (sam != null) {
                beanDef.setBeanClass(sam.getIntrospectedClass())
            } else {
                beanDef.setBeanClassName(configClass.getMetadata().getClassName())
            }
        } else {
            // instance @Bean method

            // instance @Bean method
            beanDef.setFactoryBeanName(configClass.getBeanName())
            beanDef.setUniqueFactoryMethodName(methodName)
        }

        if (metadata is StandardMethodMetadata) {
            beanDef.setResolvedFactoryMethod(metadata.getIntrospectedMethod())
        }

        beanDef.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR)
        AnnotationConfigUtil.processCommonDefinitionAnnotations(beanDef, metadata)
        val autowireCandidate = bean.getBoolean("autowireCandidate")
        if (!autowireCandidate) {
            beanDef.setAutowireCandidate(false)
        }
        val initMethodName = bean.getString("initMethod")
        if (initMethodName.isNotEmpty()) {
            beanDef.setInitMethodName(initMethodName)
        }
        val destroyMethodName = bean.getString("destroyMethod")
        beanDef.setDestroyMethodName(destroyMethodName)

        // Consider scoping
        var proxyMode = ScopedProxyMode.NO
        val attributes = AnnotationConfigUtil.attributesFor(metadata, Scope::class.java)
        if (attributes != null) {
            beanDef.setScope(attributes.getString("value"))
            proxyMode = attributes.getEnum("proxyMode")
            if (proxyMode == ScopedProxyMode.DEFAULT) {
                proxyMode = ScopedProxyMode.NO
            }
        }

        // Replace the original bean definition with the target one, if necessary
        var beanDefToRegister = beanDef

        if (logger.isTraceEnabled) {
            logger.trace(
                "Registering bean definition for @Bean method ${
                    configClass.getMetadata().getClassName()
                }.${beanName}()",
            )
        }
        registry.registerBeanDefinition(beanName, beanDefToRegister)
    }

    fun isOverriddenByExistingDefinition(beanMethod: BeanMethod, beanName: String): Boolean {
        if (!registry.containsBeanDefinition(beanName)) {
            return false
        }
        val existingBeanDef = registry.getBeanDefinition(beanName)

        // Is the existing bean definition one that was created from a configuration class?
        // -> allow the current bean method to override, since both are at second-pass level.
        // However, if the bean method is an overloaded case on the same configuration class,
        // preserve the existing bean definition.
        if (existingBeanDef is ConfigurationClassBeanDefinition) {
            return existingBeanDef.getMetadata().getClassName() == beanMethod.getConfigurationClass().getMetadata()
                .getClassName()
        }

        // A bean definition resulting from a component scan can be silently overridden
        // by an @Bean method, as of 4.2...
        if (existingBeanDef is ScannedGenericBeanDefinition) {
            return false
        }

        // At this point, it's a top-level override (probably XML), just having been parsed
        // before configuration class processing kicks in...
        if (registry is DefaultListableBeanFactory &&
            !registry.isAllowBeanDefinitionOverriding()
        ) {
            throw BeanDefinitionStoreException(
                beanMethod.getConfigurationClass().getResource().getDescription(),
                beanName, "@Bean definition illegally overridden by existing bean definition: $existingBeanDef"
            )
        }
        if (logger.isDebugEnabled) {
            logger.debug(
                "Skipping bean definition for $beanMethod: a definition for bean '$beanName' " +
                        "already exists. This top-level bean definition is considered as an override."
            )
        }
        return true
    }

    private class ConfigurationClassBeanDefinition : RootBeanDefinition, AnnotatedBeanDefinition {

        private val annotationMetadata: AnnotationMetadata

        private val factoryMethodMetadata: MethodMetadata

        private val derivedBeanName: String

        constructor(
            configClass: ConfigurationClass, beanMethodMetadata: MethodMetadata, derivedBeanName: String
        ) {
            this.annotationMetadata = configClass.getMetadata()
            this.factoryMethodMetadata = beanMethodMetadata
            this.derivedBeanName = derivedBeanName
            setResource(configClass.getResource())
            setLenientConstructorResolution(false)
        }

        constructor(
            original: RootBeanDefinition,
            configClass: ConfigurationClass, beanMethodMetadata: MethodMetadata, derivedBeanName: String
        ) : super(original) {
            this.annotationMetadata = configClass.getMetadata()
            this.factoryMethodMetadata = beanMethodMetadata
            this.derivedBeanName = derivedBeanName
        }

        private constructor(original: ConfigurationClassBeanDefinition) : super(original) {
            this.annotationMetadata = original.annotationMetadata
            this.factoryMethodMetadata = original.factoryMethodMetadata
            this.derivedBeanName = original.derivedBeanName
        }

        override fun getMetadata(): AnnotationMetadata {
            return annotationMetadata
        }

        override fun cloneBeanDefinition(): ConfigurationClassBeanDefinition {
            return ConfigurationClassBeanDefinition(this)
        }

    }

}