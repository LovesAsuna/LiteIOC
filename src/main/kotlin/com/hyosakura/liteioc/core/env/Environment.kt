package com.hyosakura.liteioc.core.env

/**
 * @author LovesAsuna
 **/
interface Environment : PropertyResolver {

    fun getActiveProfiles(): Array<String>?

    fun getDefaultProfiles(): Array<String>?

    @Deprecated("as of 5.1 in favor of {@link #acceptsProfiles(Profiles)}")
    fun acceptsProfiles(vararg profiles: String?): Boolean

    fun acceptsProfiles(profiles: Profiles?): Boolean

}