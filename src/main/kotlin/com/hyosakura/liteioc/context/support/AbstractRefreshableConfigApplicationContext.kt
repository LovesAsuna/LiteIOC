package com.hyosakura.liteioc.context.support

import com.hyosakura.liteioc.bean.factory.BeanNameAware
import com.hyosakura.liteioc.bean.factory.InitializingBean
import com.hyosakura.liteioc.context.ApplicationContext

/**
 * @author LovesAsuna
 **/
abstract class AbstractRefreshableConfigApplicationContext : AbstractRefreshableApplicationContext, BeanNameAware,
    InitializingBean {

    private var configLocations: Array<String>? = null


    private var setIdCalled = false

    constructor() : super()

    constructor(context: ApplicationContext?) : super(context)

    open fun setConfigLocations(vararg locations: String) {
        configLocations = Array(locations.size) { i ->
            resolvePath(locations[i])!!.trim { it <= ' ' }
        }
    }

    protected open fun resolvePath(path: String): String? {
        return getEnvironment().resolveRequiredPlaceholders(path)
    }

    override fun setBeanName(name: String) {
        if (!this.setIdCalled) {
            super.setId(name)
            setDisplayName("ApplicationContext '$name'")
        }
    }

    override fun setId(id: String) {
        super.setId(id)
        this.setIdCalled = true
    }

    override fun afterPropertiesSet() {
        if (!isActive()) {
            refresh()
        }
    }

    protected open fun getConfigLocations(): Array<String>? {
        return if (configLocations != null) configLocations else getDefaultConfigLocations()
    }

    protected open fun getDefaultConfigLocations(): Array<String>? {
        return null
    }

}