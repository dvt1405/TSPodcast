package tss.t.shared.config

/**
 * Remote configuration seen by shared code, the seam for Firebase Remote Config.
 *
 * Android's `tss.t.sharedlibrary.utils.ConfigAPI` has typed getters (`getJSONObject`, …);
 * shared only needs the raw string and parses it itself. Until Firebase is set up for iOS
 * the Swift side passes an implementation that always returns `nil`, and shared falls back
 * to the bundled defaults.
 */
interface ConfigAPI {
    /** The value of [key], or null when the key is unknown or Remote Config is not ready. */
    fun getString(key: String): String?
}

/** Remote Config keys, same names as Android's `tss.t.podcasts.ConfigKeys`. */
object ConfigKeys {
    /** `{"feedId": [..], "filterText": [..]}`; replaces the bundled `blacklist.json`. */
    const val KEY_BLACK_LIST = "ts_podcast_black_list"

    /**
     * The news hub configuration (M6 §3.4), same schema as `shared/assets/news_hub_default.json`;
     * replaces it when it decodes. Not read by Android.
     */
    const val KEY_NEWS_HUB = "news_hub_v1"
}
