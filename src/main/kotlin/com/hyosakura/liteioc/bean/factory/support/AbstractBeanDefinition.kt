package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.BeanDefinition.Companion.SCOPE_PROTOTYPE
import com.hyosakura.liteioc.bean.BeanDefinition.Companion.SCOPE_SINGLETON
import com.hyosakura.liteioc.bean.MutablePropertyValues
import com.hyosakura.liteioc.bean.factory.config.AutowireCapableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.ConstructorArgumentValues
import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.core.io.Resource
import com.hyosakura.liteioc.util.ClassUtil
import java.util.function.Supplier

/**
 * @author LovesAsuna
 **/
abstract class AbstractBeanDefinition : BeanDefinition {

    companion object {

        const val SCOPE_DEFAULT = ""

        const val AUTOWIRE_NO: Int = AutowireCapableBeanFactory.AUTOWIRE_NO

        const val AUTOWIRE_BY_NAME: Int = AutowireCapableBeanFactory.AUTOWIRE_BY_NAME

        const val AUTOWIRE_BY_TYPE: Int = AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE

        const val AUTOWIRE_CONSTRUCTOR = AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR

        @Deprecated("If you are using mixed autowiring strategies, prefer annotation-based autowiring for clearer demarcation of autowiring needs.")
        const val AUTOWIRE_AUTODETECT = AutowireCapableBeanFactory.AUTOWIRE_AUTODETECT

        const val DEPENDENCY_CHECK_NONE = 0

        const val DEPENDENCY_CHECK_OBJECTS = 1

        const val DEPENDENCY_CHECK_SIMPLE = 2

        const val DEPENDENCY_CHECK_ALL = 3

    }

    private var dependencyCheck = DEPENDENCY_CHECK_NONE

    private var scope: String? = SCOPE_DEFAULT

    private var abstractFlag = false

    private var lazyInit: Boolean = false

    private var description: String? = null

    private var lenientConstructorResolution = true

    private var factoryBeanName: String? = null

    private var factoryMethodName: String? = null

    private var constructorArgumentValues: ConstructorArgumentValues? = null

    private var propertyValues: MutablePropertyValues? = null

    private var autowireCandidate = true

    private var primary = false

    private var dependsOn: Array<String>? = null

    private var autowireMode: Int = AUTOWIRE_NO

    private var methodOverrides = MethodOverrides()

    private var instanceSupplier: Supplier<*>? = null

    private var nonPublicAccessAllowed = true

    private var synthetic = false

    private val attributes: MutableMap<String, Any?> = LinkedHashMap()

    private var source: Any? = null

    private var resource: Resource? = null

    private var initMethodNames: Array<String>? = null

    private var destroyMethodNames: Array<String>? = null

    @Volatile
    private var beanClass: Any? = null

    constructor() : this(null, null)

    constructor(cargs: ConstructorArgumentValues?, pvs: MutablePropertyValues?) {
        this.constructorArgumentValues = cargs
        this.propertyValues = pvs
    }

    constructor(original: BeanDefinition) {
        setParentName(original.getParentName())
        setBeanClassName(original.getBeanClassName())
        setScope(original.getScope())
        setAbstract(original.isAbstract())
        setFactoryBeanName(original.getFactoryBeanName())
        setFactoryMethodName(original.getFactoryMethodName())
        setSource(original.getSource())
        if (original is AbstractBeanDefinition) {
            if (original.hasBeanClass()) {
                setBeanClass(original.getBeanClass())
            }
            if (original.hasConstructorArgumentValues()) {
                setConstructorArgumentValues(ConstructorArgumentValues(original.getConstructorArgumentValues()))
            }
            if (original.hasPropertyValues()) {
                setPropertyValues(MutablePropertyValues(original.getPropertyValues()))
            }
            if (original.hasMethodOverrides()) {
                original.setMethodOverrides(original.getMethodOverrides())
            }
            setLazyInit(original.lazyInit)
            setAutowireMode(original.getAutowireMode())
            setDependencyCheck(original.getDependencyCheck())
            setDependsOn(*(original.getDependsOn() ?: emptyArray()))
            setAutowireCandidate(original.isAutowireCandidate())
            setPrimary(original.isPrimary())
            setInstanceSupplier(original.getInstanceSupplier())
            setNonPublicAccessAllowed(original.isNonPublicAccessAllowed())
            setLenientConstructorResolution(original.isLenientConstructorResolution())
            setSynthetic(original.isSynthetic())
            setResource(original.getResource())
        }
    }

    override fun setInitMethodName(initMethodName: String?) {
        initMethodNames = if (initMethodName != null) arrayOf(initMethodName) else null
    }

    override fun getInitMethodName(): String? {
        return if (!initMethodNames.isNullOrEmpty()) initMethodNames!![0] else null
    }

    override fun setDestroyMethodName(destroyMethodName: String?) {
        destroyMethodNames = if (destroyMethodName != null) arrayOf(destroyMethodName) else null
    }

    override fun getDestroyMethodName(): String? {
        return if (!destroyMethodNames.isNullOrEmpty()) destroyMethodNames!![0] else null
    }

    fun setResource(resource: Resource?) {
        this.resource = resource
    }

    fun getResource(): Resource? {
        return resource
    }

    open fun setSource(source: Any?) {
        this.source = source
    }

    override fun getSource(): Any? {
        return source
    }

    override fun setAttribute(name: String, value: Any?) {
        if (value != null) {
            this.attributes[name] = value
        } else {
            this.attributes.remove(name)
        }
    }

    override fun getAttribute(name: String): Any? = this.attributes[name]

    fun overrideFrom(other: BeanDefinition) {
        if (!other.getScope().isNullOrEmpty()) {
            setScope(other.getScope())
        }
        if (other is AbstractBeanDefinition) {
            if (other.hasPropertyValues()) {
                setPropertyValues(MutablePropertyValues(other.getPropertyValues()))
            }
            setLazyInit(other.lazyInit)
        }
    }

    open fun applyDefaults(defaults: BeanDefinitionDefaults) {
        setLazyInit(defaults.lazyInit)
        setAutowireMode(defaults.autowireMode)
        setDependencyCheck(defaults.dependencyCheck)
        setInitMethodName(defaults.initMethodName)
        setDestroyMethodName(defaults.destroyMethodName)
    }

    fun getResolvedAutowireMode(): Int {
        return if (this.autowireMode == AUTOWIRE_AUTODETECT) {
            // Work out whether to apply setter autowiring or constructor autowiring.
            // If it has a no-arg constructor it's deemed to be setter autowiring,
            // otherwise we'll try constructor autowiring.
            val constructors = getBeanClass().constructors
            for (constructor in constructors) {
                if (constructor.parameterCount == 0) {
                    return AUTOWIRE_BY_TYPE
                }
            }
            AUTOWIRE_CONSTRUCTOR
        } else {
            this.autowireMode
        }
    }

    override fun getScope(): String? = scope

    override fun setScope(scope: String?) {
        this.scope = scope
    }

    open fun isSynthetic(): Boolean {
        return synthetic
    }

    open fun setSynthetic(synthetic: Boolean) {
        this.synthetic = synthetic
    }

    override fun setBeanClassName(beanClassName: String?) {
        this.beanClass = beanClassName
    }

    override fun getBeanClassName(): String? {
        return if (this.beanClass is Class<*>) {
            (this.beanClass as Class<*>).name
        } else {
            this.beanClass as String?
        }
    }

    fun setInstanceSupplier(instanceSupplier: Supplier<*>?) {
        this.instanceSupplier = instanceSupplier
    }

    fun getInstanceSupplier(): Supplier<*>? {
        return instanceSupplier
    }

    open fun setLenientConstructorResolution(lenientConstructorResolution: Boolean) {
        this.lenientConstructorResolution = lenientConstructorResolution
    }

    open fun isLenientConstructorResolution(): Boolean {
        return lenientConstructorResolution
    }

    fun setBeanClass(beanClass: Class<*>?) {
        this.beanClass = beanClass
    }

    @Throws(IllegalStateException::class)
    fun getBeanClass(): Class<*> {
        val beanClassObject =
            this.beanClass ?: throw IllegalStateException("No bean class specified on bean definition")
        if (beanClassObject !is Class<*>) {
            throw IllegalStateException("Bean class name [$beanClassObject] has not been resolved into an actual Class")
        }
        return beanClassObject
    }

    fun hasBeanClass(): Boolean {
        return this.beanClass is Class<*>
    }

    open fun setAutowireMode(autowireMode: Int) {
        this.autowireMode = autowireMode
    }

    open fun getAutowireMode(): Int {
        return this.autowireMode
    }

    override fun setAutowireCandidate(autowireCandidate: Boolean) {
        this.autowireCandidate = autowireCandidate
    }

    override fun isAutowireCandidate(): Boolean {
        return this.autowireCandidate
    }

    open fun setPrimary(primary: Boolean) {
        this.primary = primary
    }

    open fun isPrimary(): Boolean {
        return primary
    }

    fun setNonPublicAccessAllowed(nonPublicAccessAllowed: Boolean) {
        this.nonPublicAccessAllowed = nonPublicAccessAllowed
    }

    fun isNonPublicAccessAllowed(): Boolean {
        return nonPublicAccessAllowed
    }

    @Throws(ClassNotFoundException::class)
    fun resolveBeanClass(classLoader: ClassLoader?): Class<*>? {
        val className = getBeanClassName() ?: return null
        val resolvedClass: Class<*> = ClassUtil.forName(className, classLoader)
        beanClass = resolvedClass
        return resolvedClass
    }

    @Throws(BeanDefinitionValidationException::class)
    open fun validate() {
        if (hasBeanClass()) {
            prepareMethodOverrides()
        }
    }

    @Throws(BeanDefinitionValidationException::class)
    open fun prepareMethodOverrides() {
        // Check that lookup methods exist and determine their overloaded status.
        if (hasMethodOverrides()) {
            getMethodOverrides().getOverrides().forEach { mo: MethodOverride -> this.prepareMethodOverride(mo) }
        }
    }

    @Throws(BeanDefinitionValidationException::class)
    protected open fun prepareMethodOverride(mo: MethodOverride) {
        val count: Int = ClassUtil.getMethodCountForName(getBeanClass(), mo.getMethodName())
        if (count == 0) {
            throw BeanDefinitionValidationException(
                "Invalid method override: no method with name '" + mo.getMethodName() +
                        "' on class [" + getBeanClassName() + "]"
            )
        } else if (count == 1) {
            // Mark override as not overloaded, to avoid the overhead of arg type checking.
            mo.setOverloaded(false)
        }
    }

    fun hasMethodOverrides(): Boolean {
        return !this.methodOverrides.isEmpty()
    }

    open fun getMethodOverrides(): MethodOverrides {
        return methodOverrides
    }

    open fun setMethodOverrides(methodOverrides: MethodOverrides) {
        this.methodOverrides = methodOverrides
    }

    override fun isSingleton(): Boolean {
        return SCOPE_SINGLETON == this.scope || SCOPE_DEFAULT == this.scope
    }

    override fun isPrototype(): Boolean {
        return SCOPE_PROTOTYPE == this.scope
    }

    override fun isAbstract(): Boolean {
        return this.abstractFlag
    }

    fun setAbstract(abstractFlag: Boolean) {
        this.abstractFlag = abstractFlag
    }

    override fun getDependsOn(): Array<String>? = this.dependsOn

    override fun setDependsOn(vararg dependsOn: String) {
        @Suppress("UNCHECKED_CAST")
        this.dependsOn = dependsOn as Array<String>
    }

    override fun isLazyInit(): Boolean = this.lazyInit

    override fun setLazyInit(lazyInit: Boolean) {
        this.lazyInit = lazyInit
    }

    override fun getDescription(): String? = this.description

    override fun setDescription(description: String?) {
        this.description = description
    }


    override fun setFactoryBeanName(factoryBeanName: String?) {
        this.factoryBeanName = factoryBeanName
    }

    override fun getFactoryBeanName(): String? {
        return this.factoryBeanName
    }

    override fun setFactoryMethodName(factoryMethodName: String?) {
        this.factoryMethodName = factoryMethodName
    }

    override fun getFactoryMethodName(): String? {
        return this.factoryMethodName
    }

    override fun getConstructorArgumentValues(): ConstructorArgumentValues {
        if (constructorArgumentValues == null) {
            this.constructorArgumentValues = ConstructorArgumentValues()
        }
        return this.constructorArgumentValues!!
    }

    open fun setConstructorArgumentValues(constructorArgumentValues: ConstructorArgumentValues) {
        this.constructorArgumentValues = constructorArgumentValues
    }

    override fun getPropertyValues(): MutablePropertyValues {
        if (this.propertyValues == null) {
            this.propertyValues = MutablePropertyValues()
        }
        return this.propertyValues!!
    }

    abstract fun cloneBeanDefinition(): AbstractBeanDefinition

    open fun setPropertyValues(propertyValues: MutablePropertyValues) {
        this.propertyValues = propertyValues
    }

    fun setDependencyCheck(dependencyCheck: Int) {
        this.dependencyCheck = dependencyCheck
    }

    fun getDependencyCheck(): Int {
        return this.dependencyCheck
    }

    override fun getResolvableType(): ResolvableType {
        return if (hasBeanClass()) ResolvableType.forClass(getBeanClass()) else ResolvableType.NONE
    }


    override fun toString(): String {
        val sb = StringBuilder("class [")
        sb.append(getBeanClassName()).append(']')
        sb.append("; scope=").append(this.scope)
        sb.append("; abstract=").append(this.abstractFlag)
        sb.append("; lazyInit=").append(this.lazyInit)
        sb.append("; autowireMode=").append(this.autowireMode)
        sb.append("; dependencyCheck=").append(this.dependencyCheck)
        sb.append("; autowireCandidate=").append(this.autowireCandidate)
        sb.append("; primary=").append(this.primary)
        sb.append("; initMethodNames=").append(this.initMethodNames)
        sb.append("; destroyMethodNames=").append(this.destroyMethodNames)
        if (this.resource != null) {
            sb.append("; defined in ").append(this.resource!!.getDescription())
        }
        return sb.toString()
    }

}