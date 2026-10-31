package tss.t.podcast.ui.screens.podcastsdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import tss.t.coreapi.models.Episode
import tss.t.podcast.R
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import java.text.Normalizer

/** Accent-insensitive episode search inside one show (iOS `VietnameseFold` + `EpisodeFilter`). */
object EpisodeSearch {
    /** The field shows only for shows with at least this many episodes. */
    const val MIN_EPISODES = 5

    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    /** Lowercase, without Vietnamese diacritics (đ → d) and with single spaces. */
    fun fold(text: String?): String =
        Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase()
            .replace(spaces, " ")
            .trim()

    /** Episodes whose title or summary contains every word of [query]. */
    fun filter(episodes: List<Episode>, query: String): List<Episode> {
        val words = fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return episodes
        return episodes.filter { episode ->
            val haystack = fold(episode.title) + " " + fold(
                HtmlCompat.fromHtml(episode.description.orEmpty(), HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
            )
            words.all { haystack.contains(it) }
        }
    }
}

@Composable
internal fun ShowSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text(stringResource(R.string.show_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.show_search_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        if (resultCount != null) {
            Text(
                text = if (resultCount == 0) {
                    stringResource(R.string.show_search_empty)
                } else {
                    stringResource(R.string.show_search_count, resultCount)
                },
                style = TextStyles.SubTitle4,
                color = Colors.Gray60,
                modifier = Modifier.padding(top = 6.dp, start = 8.dp)
            )
        }
    }
}
