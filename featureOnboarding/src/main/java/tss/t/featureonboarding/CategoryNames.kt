package tss.t.featureonboarding

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Names of the PodcastIndex categories (`categories.json` ids) in the app language, so the topic
 * picker reads naturally (iOS+ "bản địa hoá tên danh mục"). The names live in string resources
 * `category_<id>` (`categories.xml` of each values folder); the English API name stays the search key and is
 * the fallback for an id without a resource.
 */
object CategoryNames {
    /** Shown first under "Phổ biến": what Vietnamese listeners pick most, in this order. */
    val POPULAR_IDS: List<Int> = listOf(
        55, 9, 25, 28, 41, 16, 20, 33, 29, 102, 53, 12, 85, 84, 6, 96, 23, 77,
    )

    /** `category_<id>` for ids 1..112, indexed by `id - 1`. */
    private val names = intArrayOf(
        R.string.category_1, R.string.category_2, R.string.category_3, R.string.category_4,
        R.string.category_5, R.string.category_6, R.string.category_7, R.string.category_8,
        R.string.category_9, R.string.category_10, R.string.category_11, R.string.category_12,
        R.string.category_13, R.string.category_14, R.string.category_15, R.string.category_16,
        R.string.category_17, R.string.category_18, R.string.category_19, R.string.category_20,
        R.string.category_21, R.string.category_22, R.string.category_23, R.string.category_24,
        R.string.category_25, R.string.category_26, R.string.category_27, R.string.category_28,
        R.string.category_29, R.string.category_30, R.string.category_31, R.string.category_32,
        R.string.category_33, R.string.category_34, R.string.category_35, R.string.category_36,
        R.string.category_37, R.string.category_38, R.string.category_39, R.string.category_40,
        R.string.category_41, R.string.category_42, R.string.category_43, R.string.category_44,
        R.string.category_45, R.string.category_46, R.string.category_47, R.string.category_48,
        R.string.category_49, R.string.category_50, R.string.category_51, R.string.category_52,
        R.string.category_53, R.string.category_54, R.string.category_55, R.string.category_56,
        R.string.category_57, R.string.category_58, R.string.category_59, R.string.category_60,
        R.string.category_61, R.string.category_62, R.string.category_63, R.string.category_64,
        R.string.category_65, R.string.category_66, R.string.category_67, R.string.category_68,
        R.string.category_69, R.string.category_70, R.string.category_71, R.string.category_72,
        R.string.category_73, R.string.category_74, R.string.category_75, R.string.category_76,
        R.string.category_77, R.string.category_78, R.string.category_79, R.string.category_80,
        R.string.category_81, R.string.category_82, R.string.category_83, R.string.category_84,
        R.string.category_85, R.string.category_86, R.string.category_87, R.string.category_88,
        R.string.category_89, R.string.category_90, R.string.category_91, R.string.category_92,
        R.string.category_93, R.string.category_94, R.string.category_95, R.string.category_96,
        R.string.category_97, R.string.category_98, R.string.category_99, R.string.category_100,
        R.string.category_101, R.string.category_102, R.string.category_103, R.string.category_104,
        R.string.category_105, R.string.category_106, R.string.category_107, R.string.category_108,
        R.string.category_109, R.string.category_110, R.string.category_111, R.string.category_112,
    )

    @StringRes
    fun nameRes(id: Int): Int? = names.getOrNull(id - 1)

    /** The topic illustration of category [id] (onboarding icons), if it has one. */
    fun iconRes(id: Int): Int? = _mapIcon[id]

    /** The localized name of category [id], or [fallback] (the English API name). */
    fun name(context: Context, id: Int, fallback: String): String =
        nameRes(id)?.let(context::getString) ?: fallback

    /** The localized name of category [id], or [fallback] (the English API name). */
    @Composable
    fun name(id: Int, fallback: String): String =
        nameRes(id)?.let { stringResource(it) } ?: fallback
}
