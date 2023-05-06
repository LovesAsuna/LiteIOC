package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.core.ConversionException
import com.hyosakura.liteioc.core.convert.ConverterNotFoundException
import com.hyosakura.liteioc.core.convert.Property
import com.hyosakura.liteioc.core.convert.TypeDescriptor
import org.jetbrains.annotations.Nullable
import java.beans.PropertyChangeEvent
import java.beans.PropertyDescriptor

/**
 * @author LovesAsuna
 **/
class BeanWrapperImpl : AbstractPropertyAccessor, BeanWrapper {

    var wrappedObject: Any? = null

    private var cachedIntrospectionResults: CachedIntrospectionResults? = null

    constructor()

    constructor(`object`: Any?) {
        this.wrappedObject = `object`
        this.typeConverterDelegate = TypeConverterDelegate(this)
    }

    override fun getWrappedInstance(): Any {
        requireNotNull(this.wrappedObject) { "No wrapped object" }
        return this.wrappedObject!!
    }

    override fun getWrappedClass(): Class<*> {
        return getWrappedInstance().javaClass
    }

    override fun getPropertyDescriptors(): Array<PropertyDescriptor> {
        return getCachedIntrospectionResults().getPropertyDescriptors()
    }

    override fun getPropertyDescriptor(propertyName: String): PropertyDescriptor {
        return PropertyDescriptor(propertyName, wrappedObject!!::class.java)
    }

    override fun isWritableProperty(propertyName: String): Boolean {
        return true
    }

    private fun getCachedIntrospectionResults(): CachedIntrospectionResults {
        if (this.cachedIntrospectionResults == null) {
            this.cachedIntrospectionResults = CachedIntrospectionResults.forClass(getWrappedClass())
        }
        return this.cachedIntrospectionResults!!
    }

    fun setBeanInstance(`object`: Any?) {
        wrappedObject = `object`
    }

    override fun setPropertyValue(propertyName: String, value: Any?) {
        TODO("Not yet implemented")
    }

    private fun property(pd: PropertyDescriptor): Property {
        val gpd = pd as GenericTypeAwarePropertyDescriptor
        return Property(gpd.getBeanClass(), gpd.readMethod, gpd.writeMethod, gpd.name)
    }

    @Throws(TypeMismatchException::class)
    fun convertForProperty(value: Any?, propertyName: String): Any? {
        val cachedIntrospectionResults = getCachedIntrospectionResults()
        val pd = cachedIntrospectionResults.getPropertyDescriptor(propertyName) ?: throw InvalidPropertyException(
            getWrappedClass(), propertyName, "No property '$propertyName' found"
        )
        var td = cachedIntrospectionResults.getTypeDescriptor(pd)
        if (td == null) {
            td = cachedIntrospectionResults.addTypeDescriptor(pd, TypeDescriptor(property(pd)))
        }
        return convertForProperty(propertyName, null, value, td)
    }
    @Throws(TypeMismatchException::class)
    private fun convertForProperty(
        propertyName: String, oldValue: Any?, newValue: Any?, td: TypeDescriptor
    ): Any? {
        return convertIfNecessary(propertyName, oldValue, newValue, td.type, td)
    }

    @Throws(TypeMismatchException::class)
    private fun convertIfNecessary(
        propertyName: String?, oldValue: Any?,
        newValue: Any?, requiredType: Class<*>?, td: TypeDescriptor?
    ): Any? {
        return try {
            this.typeConverterDelegate.convertIfNecessary(propertyName, oldValue, newValue, requiredType, td)
        } catch (ex: ConverterNotFoundException) {
            val pce = PropertyChangeEvent(this.wrappedObject, propertyName, oldValue, newValue)
            throw ConversionNotSupportedException(pce, requiredType, ex)
        } catch (ex: IllegalStateException) {
            val pce = PropertyChangeEvent(this.wrappedObject, propertyName, oldValue, newValue)
            throw ConversionNotSupportedException(pce, requiredType, ex)
        } catch (ex: ConversionException) {
            val pce = PropertyChangeEvent(this.wrappedObject, propertyName, oldValue, newValue)
            throw TypeMismatchException(pce, requiredType, ex)
        } catch (ex: IllegalArgumentException) {
            val pce = PropertyChangeEvent(this.wrappedObject, propertyName, oldValue, newValue)
            throw TypeMismatchException(pce, requiredType, ex)
        }
    }

}