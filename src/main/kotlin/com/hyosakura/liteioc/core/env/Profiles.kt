package com.hyosakura.liteioc.core.env

import java.util.function.Predicate

/**
 * @author LovesAsuna
 **/

@FunctionalInterface
fun interface Profiles {

    fun matches(activeProfiles: Predicate<String>): Boolean

    fun of(vararg profiles: String): Profiles {
        return ProfilesParser.parse(*profiles)
    }

}