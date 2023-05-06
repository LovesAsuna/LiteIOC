package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.core.ConversionException
import com.hyosakura.liteioc.core.convert.ConversionService
import com.hyosakura.liteioc.core.convert.ConverterNotFoundException
import com.hyosakura.liteioc.core.convert.TypeDescriptor
import org.jetbrains.annotations.Nullable

/**
 * @author LovesAsuna
 **/
abstract class TypeConverterSupport : TypeConverter {

    lateinit var typeConverterDelegate: TypeConverterDelegate

    private var conversionService: ConversionService? = null

    fun getConversionService(): ConversionService? {
        return this.conversionService
    }

    fun setConversionService(conversionService: ConversionService?) {
        this.conversionService = conversionService
    }

    @Throws(TypeMismatchException::class)
    override fun <T> convertIfNecessary(value: Any?, requiredType: Class<T>?): T? {
        return convertIfNecessary(value, requiredType, TypeDescriptor.valueOf(requiredType))
    }

    @Throws(TypeMismatchException::class)
    override fun <T> convertIfNecessary(
        value: Any?, requiredType: Class<T>?,
        typeDescriptor: TypeDescriptor?
    ): T? {
        return try {
            this.typeConverterDelegate.convertIfNecessary(null, null, value, requiredType, typeDescriptor)
        } catch (ex: ConverterNotFoundException) {
            throw ConversionNotSupportedException(value, requiredType, ex)
        } catch (ex: IllegalStateException) {
            throw ConversionNotSupportedException(value, requiredType, ex)
        } catch (ex: ConversionException) {
            throw TypeMismatchException(value, requiredType, ex)
        } catch (ex: IllegalArgumentException) {
            throw TypeMismatchException(value, requiredType, ex)
        }
    }

}