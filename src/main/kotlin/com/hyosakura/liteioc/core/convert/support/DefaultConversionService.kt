package com.hyosakura.liteioc.core.convert.support

import com.hyosakura.liteioc.core.convert.ConversionService
import com.hyosakura.liteioc.core.convert.GenericConversionService

/**
 * @author LovesAsuna
 **/
class DefaultConversionService : GenericConversionService() {

    companion object {

        @Volatile
        private var sharedInstance: DefaultConversionService? = null

        fun getSharedInstance(): ConversionService? {
            var cs = sharedInstance
            if (cs == null) {
                synchronized(DefaultConversionService::class.java) {
                    cs = sharedInstance
                    if (cs == null) {
                        cs = DefaultConversionService()
                        sharedInstance = cs
                    }
                }
            }
            return cs
        }

    }

}