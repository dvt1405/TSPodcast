package tss.t.core.storage

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import tss.t.coreapi.models.CategoryRes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPref @Inject constructor(
    @ApplicationContext
    context: Context
) {
    val _sharedPref by lazy {
        context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
    }

    val gson by lazy { Gson() }

    fun clear(key: String) {
        _sharedPref.edit()
            .remove(key)
            .apply()
    }

    fun <T> save(key: String, value: T) {
        val edit = _sharedPref.edit()
        when (value!!::class) {
            Boolean::class -> edit.putBoolean(key, value as Boolean)
            Int::class -> edit.putInt(key, value as Int)
            Float::class -> edit.putFloat(key, value as Float)
            Long::class -> edit.putLong(key, value as Long)
            String::class -> edit.putString(key, value as String)
            else -> {
                edit.putString(key, gson.toJson(value))
            }
        }

        edit.apply()
    }

    inline fun <reified T> get(key: String): T? {
        if (!_sharedPref.contains(key)) return null
        return when (T::class.java) {
            Boolean::class.java, Boolean::class.javaPrimitiveType -> {
                _sharedPref.getBoolean(key, false) as T
            }

            Float::class.java, Float::class.javaPrimitiveType -> {
                _sharedPref.getFloat(key, -1f) as T
            }

            Int::class.java, Int::class.javaPrimitiveType -> {
                _sharedPref.getInt(key, Int.MIN_VALUE) as T
            }

            Long::class.java, Long::class.javaPrimitiveType -> {
                _sharedPref.getLong(key, -1L) as T
            }

            String::class.java, String::class.javaPrimitiveType -> {
                _sharedPref.getString(key, null) as? T
            }

            else -> {
                val jsStr = _sharedPref.getString(key, null) ?: return null
                return gson.fromJson(jsStr, T::class.java)
            }
        }
    }

    companion object {
        private const val TAG = "TSSharedPref"
    }
}

fun SharedPref.isOnboardingFinished() = get<Boolean>("OnboardingFinished") ?: false
fun SharedPref.saveOnboardingFinished(isFinished: Boolean) {

}

fun SharedPref.saveListPodcastCategory(category: CategoryRes) {
    save("CategoryRes", category)
}

fun SharedPref.getListPodcastCategory(): CategoryRes? {
    return runCatching {
        get<CategoryRes>("CategoryRes")
    }.getOrNull()
}