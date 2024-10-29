package tss.t.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import tss.t.coreapi.models.CategoryRes
import java.lang.reflect.Array
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.Objects
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPref @Inject constructor(
    @ApplicationContext
    context: Context
) {
    val _sharedPref by lazy {
        EncryptedSharedPreferences.create(
            TAG,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val gson by lazy { Gson() }

    fun clear(key: String) {
        _sharedPref.edit()
            .remove(key)
            .apply()
    }

    inline fun <reified T> save(key: String, value: T) {
        val edit = _sharedPref.edit()
        when (T::class.java) {
            Boolean::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaObjectType -> edit.putBoolean(key, value as Boolean)

            Int::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaObjectType -> edit.putInt(key, value as Int)

            Float::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaObjectType -> edit.putFloat(key, value as Float)

            Long::class.java,
            Long::class.javaPrimitiveType,
            Long::class.javaObjectType -> edit.putLong(key, value as Long)

            String::class.java -> edit.putString(key, value as String)

            else -> {
                edit.putString(key, gson.toJson(value))
            }
        }

        edit.apply()
    }

    fun getParameterUpperBound(index: Int, type: ParameterizedType): Type {
        val types = type.actualTypeArguments
        if (index >= 0 && index < types.size) {
            val paramType = types[index]
            return if (paramType is WildcardType) paramType.upperBounds[0] else paramType
        } else {
            throw IllegalArgumentException("Index " + index + " not in range [0," + types.size + ") for " + type)
        }
    }

    fun getRawType(type: Type): Class<*> {
        Objects.requireNonNull(type, "type == null")
        if (type is Class<*>) {
            return type
        } else if (type is ParameterizedType) {
            val rawType = type.rawType
            require(rawType is Class<*>)
            return rawType as Class<*>
        } else if (type is GenericArrayType) {
            val componentType = type.genericComponentType
            return Array.newInstance(getRawType(componentType), 0).javaClass
        } else if (type is TypeVariable<*>) {
            return Any::class.java
        } else if (type is WildcardType) {
            return getRawType((type as WildcardType).upperBounds[0])
        } else {
            throw java.lang.IllegalArgumentException("Expected a Class, ParameterizedType, or GenericArrayType, but <" + type + "> is of type " + type.javaClass.name)
        }
    }


    inline fun <reified T> get(key: String): T? {
        if (!_sharedPref.contains(key)) return null
        return runCatching {
            when (T::class.java) {
                Boolean::class.javaObjectType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.java -> {
                    _sharedPref.getBoolean(key, false) as? T
                }

                Float::class.javaObjectType, Float::class.javaPrimitiveType -> {
                    _sharedPref.getFloat(key, -1f) as T
                }

                Int::class.javaObjectType, Int::class.javaPrimitiveType -> {
                    _sharedPref.getInt(key, Int.MIN_VALUE) as T
                }

                Long::class.javaObjectType, Long::class.javaPrimitiveType -> {
                    _sharedPref.getLong(key, -1L) as T
                }

                String::class.java, String::class.javaPrimitiveType -> {
                    _sharedPref.getString(key, null) as? T
                }

                Set::class.java, List::class.java, Map::class.java -> {
                    val jsStr = _sharedPref.getString(key, null) ?: return null
                    return gson.fromJson(jsStr, object : TypeToken<T>() {}.type)
                }

                else -> {
                    val jsStr = _sharedPref.getString(key, null) ?: return null
                    return gson.fromJson(jsStr, T::class.java)
                }
            }
        }.onFailure {
            _sharedPref.edit()
                .remove(key)
                .apply()
        }.getOrNull()
    }

    companion object {
        private const val TAG = "TSSharedPref"
    }
}

internal const val Key_OnboardingFinished = "OnboardingFinished"
internal const val Key_CategoryRes = "CategoryRes"
internal const val Key_HasSelectFavouriteCategory = "HasSelectFavouriteCategory"
internal const val Key_ListFavouriteCategory = "ListFavouriteCategory"

fun SharedPref.isOnboardingFinished() = get<Boolean>(Key_OnboardingFinished) ?: false
fun SharedPref.saveOnboardingFinished(isFinished: Boolean) {
    save(Key_OnboardingFinished, isFinished)
}

fun SharedPref.saveListPodcastCategory(category: CategoryRes) {
    save(Key_CategoryRes, category)
}

fun SharedPref.getListPodcastCategory(): CategoryRes? {
    return runCatching {
        get<CategoryRes>(Key_CategoryRes)
    }.getOrNull()
}

fun SharedPref.saveFavouriteCategory(listFavouriteCategory: MutableSet<CategoryRes.Category>) {
    save<Boolean>(Key_HasSelectFavouriteCategory, true)
    save(Key_ListFavouriteCategory, listFavouriteCategory)
}

fun SharedPref.getFavouriteCategory() =
    get<Set<CategoryRes.Category>>(Key_ListFavouriteCategory)

fun SharedPref.hasSelectFavouriteCategory() = get<Boolean>(Key_HasSelectFavouriteCategory) ?: false