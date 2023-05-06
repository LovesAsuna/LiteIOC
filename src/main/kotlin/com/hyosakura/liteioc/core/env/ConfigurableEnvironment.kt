package com.hyosakura.liteioc.core.env

interface ConfigurableEnvironment : Environment {

    fun setActiveProfiles(vararg profiles: String)

    fun addActiveProfile(profile: String)

    fun setDefaultProfiles(vararg profiles: String)

    fun merge(parent: ConfigurableEnvironment)

}