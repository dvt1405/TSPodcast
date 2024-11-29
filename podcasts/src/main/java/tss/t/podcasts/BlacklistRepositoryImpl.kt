package tss.t.podcasts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlacklistRepositoryImpl @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    private val _scope by lazy { CoroutineScope(Dispatchers.IO) }
    private val _blackListId by lazy {
        mutableSetOf<String>()
    }

    private val _blackListKeywords by lazy {
        mutableSetOf<String>()
    }

    init {
        loadBlacklist()
    }

    fun loadBlacklist() {
        _scope.launch {
            val blackList = context.assets.open("blacklist.json")
                .bufferedReader()
                .readText()
                .let {
                    runCatching {
                        JSONObject(it)
                    }.getOrNull()
                } ?: return@launch
            val blackListId = blackList.optJSONArray("feedId")
            val blackListKeyword = blackList.optJSONArray("filterText")
            if (blackListId != null) {
                for (i in 0 until blackListId.length()) {
                    val id = blackListId.optString(i) ?: continue
                    _blackListId.add(id)
                }
            }

            if (blackListKeyword != null) {
                for (i in 0 until blackListKeyword.length()) {
                    val id = blackListKeyword.optString(i) ?: continue
                    _blackListKeywords.add(id)
                }
            }
        }
    }

    fun isInBlacklist(id: String): Boolean {
        return _blackListId.contains(id)
    }

    fun isContainKeywordsBlacklist(key: String?, filterSpace: Boolean = false): Boolean {
        if (key.isNullOrEmpty()) return false
        return _blackListKeywords.any {
            key.lowercase()
                .apply {
                    if (filterSpace) {
                        this.replace(" ", "")
                    }
                }
                .contains(it)
        }
    }
}