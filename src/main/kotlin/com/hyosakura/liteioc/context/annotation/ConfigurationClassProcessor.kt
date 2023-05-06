package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.bean.factory.config.SingletonBeanRegistry
import com.hyosakura.liteioc.bean.factory.parsing.PassThroughSourceExtractor
import com.hyosakura.liteioc.bean.factory.parsing.SourceExtractor
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.bean.factory.support.BeanNameGenerator
import com.hyosakura.liteioc.core.annotation.AnnotationConfigUtil
import com.hyosakura.liteioc.core.env.Environment
import com.hyosakura.liteioc.core.env.StandardEnvironment
import com.hyosakura.liteioc.core.io.DefaultResourceLoader
import com.hyosakura.liteioc.core.io.ResourceLoader

/**
 * @author LovesAsuna
 **/
class ConfigurationClassProcessor {

    companion object {

        val IMPORT_BEAN_NAME_GENERATOR: AnnotationBeanNameGenerator = FullyQualifiedAnnotationBeanNameGenerator.INSTANCE

    }

    private var componentScanBeanNameGenerator: BeanNameGenerator = AnnotationBeanNameGenerator.INSTANCE

    private var importBeanNameGenerator: BeanNameGenerator = IMPORT_BEAN_NAME_GENERATOR

    private var localBeanNameGeneratorSet = false

    private val registriesPostProcessed: MutableSet<Int> = HashSet()

    private val sourceExtractor: SourceExtractor = PassThroughSourceExtractor()

    private var environment: Environment? = null

    private val resourceLoader: ResourceLoader = DefaultResourceLoader()

    private var reader: ConfigurationClassBeanDefinitionReader? = null

    fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
        val registryId: Int = System.identityHashCode(registry)
        if (this.registriesPostProcessed.contains(registryId)) {
            throw IllegalStateException(
                "postProcessBeanDefinitionRegistry already called on this post-processor against $registry"
            )
        }
        this.registriesPostProcessed.add(registryId)
        processConfigBeanDefinitions(registry)
    }

    fun processConfigBeanDefinitions(registry: BeanDefinitionRegistry) {
        val configCandidates = ArrayList<BeanDefinitionHolder>()

        var candidateNames = registry.getBeanDefinitionNames()

        for (beanName in candidateNames) {
            val beanDef = registry.getBeanDefinition(beanName)

            if (ConfigurationClassUtil.checkConfigurationClassCandidate(beanDef)) {
                configCandidates.add(BeanDefinitionHolder(beanDef, beanName))
            }
        }

        if (configCandidates.isEmpty()) {
            return
        }

        if (registry is SingletonBeanRegistry) {
            if (!this.localBeanNameGeneratorSet) {
                val generator =
                    registry.getSingleton(AnnotationConfigUtil.CONFIGURATION_BEAN_NAME_GENERATOR) as BeanNameGenerator?
                if (generator != null) {
                    this.componentScanBeanNameGenerator = generator
                }
            }
        }

        if (environment == null) {
            this.environment = StandardEnvironment()
        }

        val parser = ConfigurationClassParser(
            this.environment!!,
            this.resourceLoader,
            this.componentScanBeanNameGenerator,
            registry
        )

        val candidates: MutableSet<BeanDefinitionHolder> = LinkedHashSet(configCandidates)
        val alreadyParsed: MutableSet<ConfigurationClass> = HashSet(configCandidates.size)
        do {
            parser.parse(candidates)
            val configClasses = LinkedHashSet(parser.getConfigurationClasses())
            configClasses.removeAll(alreadyParsed)

            if (this.reader == null) {
                this.reader = ConfigurationClassBeanDefinitionReader(
                    registry, this.sourceExtractor, this.resourceLoader,
                    this.environment!!, this.importBeanNameGenerator, parser.getImportRegistry()
                )
            }

            reader!!.loadBeanDefinitions(configClasses)
            alreadyParsed.addAll(configClasses)
            candidates.clear()

            if (registry.getBeanDefinitionCount() > candidateNames.size) {
                val newCandidateNames = registry.getBeanDefinitionNames()
                val oldCandidateNames: Set<String> = HashSet(listOf(*candidateNames))
                val alreadyParsedClasses: MutableSet<String> = HashSet()
                for (configurationClass in alreadyParsed) {
                    alreadyParsedClasses.add(configurationClass.getMetadata().getClassName())
                }
                for (candidateName in newCandidateNames) {
                    if (!oldCandidateNames.contains(candidateName)) {
                        val bd: BeanDefinition = registry.getBeanDefinition(candidateName)
                        if (ConfigurationClassUtil.checkConfigurationClassCandidate(bd) &&
                            !alreadyParsedClasses.contains(bd.getBeanClassName())
                        ) {
                            candidates.add(BeanDefinitionHolder(bd, candidateName))
                        }
                    }
                }
                candidateNames = newCandidateNames
            }
        } while (candidates.isNotEmpty())
    }

    fun setBeanNameGenerator(beanNameGenerator: BeanNameGenerator) {
        localBeanNameGeneratorSet = true
        componentScanBeanNameGenerator = beanNameGenerator
    }

}