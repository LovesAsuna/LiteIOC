package com.hyosakura.liteioc.core.io.support

import com.hyosakura.liteioc.core.io.Resource
import com.hyosakura.liteioc.core.io.ResourceLoader
import java.io.IOException

/**
 * @author LovesAsuna
 **/
interface ResourcePatternResolver : ResourceLoader {

    companion object {

        var CLASSPATH_ALL_URL_PREFIX = "classpath*:"

    }

    @Throws(IOException::class)
    fun getResources(locationPattern: String): Array<Resource>

}