package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.core.convert.ConversionFailedException
import com.hyosakura.liteioc.core.convert.TypeDescriptor
import com.hyosakura.liteioc.util.BeanUtil
import com.hyosakura.liteioc.util.ClassUtil
import java.lang.reflect.Array
import java.util.*

/**
 * @author LovesAsuna
 **/
class TypeConverterDelegate(private var typeConverterSupport: TypeConverterSupport? = null) {

    @Throws(IllegalArgumentException::class)
    fun <T> convertIfNecessary(propertyName: String?, oldValue: Any?, newValue: Any?, requiredType: Class<T>?, typeDescriptor: TypeDescriptor?): T? {
        var conversionAttemptEx: ConversionFailedException? = null

        val conversionService = this.typeConverterSupport!!.getConversionService()

        if (conversionService != null && newValue != null && typeDescriptor != null) {
            val sourceTypeDesc = TypeDescriptor.forObject(newValue);
            if (conversionService.canConvert(sourceTypeDesc, typeDescriptor)) {
                try {
                    return conversionService.convert(newValue, sourceTypeDesc, typeDescriptor) as T
                }
                catch (ex: ConversionFailedException) {
                    // fallback to default conversion logic below
                    conversionAttemptEx = ex;
                }
            }
        }
        return null
    }

}