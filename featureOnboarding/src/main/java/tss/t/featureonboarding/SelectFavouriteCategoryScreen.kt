package tss.t.featureonboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.TSDataState
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.placeholder
import java.text.Normalizer

private const val MIN_TOPICS = 3
private val Mist = Color(0xFFEEF3FF)
private val Ink = Color(0xFF141B34)

@Composable
fun SelectFavouriteCategoryScreen(viewmodel: OnboardingViewModel) {
    val categoryState by viewmodel.categoryState.collectAsState()
    val selected by viewmodel.listFavouriteCategory.collectAsState()
    val all = (categoryState as? TSDataState.Success<CategoryRes>)?.data?.feeds.orEmpty()
    SelectFavouriteCategoryScreen(
        listCategory = all,
        listSelected = selected,
        isLoading = categoryState is TSDataState.Loading,
        onItemSelected = { index, isSelected ->
            if (isSelected) viewmodel.putFavouriteItem(this, index) else viewmodel.removeFavouriteItem(this, index)
        },
        onSaveAndContinue = viewmodel::saveFavouriteItem
    )
}

/**
 * Topic picker in the brand tones: a gradient header with a search field, "Phổ biến" first then
 * every topic (names in the app language), cards that pop when picked, and a bottom bar with
 * the progress to the 3 needed, the picked topics (tap to remove) and the button.
 */
@Composable
fun SelectFavouriteCategoryScreen(
    listCategory: List<CategoryRes.Category> = emptyList(),
    listSelected: List<CategoryRes.Category> = emptyList(),
    isLoading: Boolean = false,
    onSaveAndContinue: () -> Unit = {},
    onItemSelected: CategoryRes.Category.(Int, Boolean) -> Unit = { _, _ -> },
) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Category names in the app language (string resources), recomputed when the locale changes.
    val names = remember(listCategory, configuration) {
        listCategory.associate { it.id to CategoryNames.name(context, it.id, it.name) }
    }
    fun nameOf(category: CategoryRes.Category): String = names[category.id] ?: category.name
    val selectedIds = remember(listSelected) { listSelected.mapTo(HashSet()) { it.id } }
    val byId = remember(listCategory) { listCategory.associateBy { it.id } }
    val popular = remember(listCategory) { CategoryNames.POPULAR_IDS.mapNotNull { byId[it] } }
    val others = remember(listCategory, names) {
        val popularIds = CategoryNames.POPULAR_IDS.toSet()
        listCategory.filterNot { it.id in popularIds }
            .sortedBy { fold(nameOf(it)) }
    }
    // Matches the localized name and the English API name, accent-insensitive.
    val searchResults = remember(listCategory, names, query) {
        val q = fold(query)
        if (q.isEmpty()) emptyList()
        else listCategory.filter {
            fold(nameOf(it)).contains(q) || fold(it.name).contains(q)
        }
    }
    fun toggle(category: CategoryRes.Category) {
        val isSelected = category.id in selectedIds
        onItemSelected(category, listCategory.indexOfFirst { it.id == category.id }, !isSelected)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        TopicsHeader(query = query, onQueryChange = { query = it })
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading -> items(18) {
                        Box(
                            Modifier
                                .height(112.dp)
                                .placeholder(true, Mist, RoundedCornerShape(20.dp))
                        )
                    }

                    query.isNotBlank() -> {
                        if (searchResults.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    stringResource(R.string.topics_empty),
                                    style = TextStyles.Body3.copy(color = Color.Gray),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(32.dp)
                                )
                            }
                        }
                        items(searchResults, key = { "s${it.id}" }) { item ->
                            TopicCard(item, item.id in selectedIds) { toggle(item) }
                        }
                    }

                    else -> {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "popular") {
                            SectionTitle(stringResource(R.string.topics_popular))
                        }
                        items(popular, key = { "p${it.id}" }) { item ->
                            TopicCard(item, item.id in selectedIds) { toggle(item) }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "all") {
                            SectionTitle(stringResource(R.string.topics_all))
                        }
                        items(others, key = { "a${it.id}" }) { item ->
                            TopicCard(item, item.id in selectedIds) { toggle(item) }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
            }
        }
        TopicsBottomBar(
            selected = listSelected,
            onRemove = { toggle(it) },
            onContinue = onSaveAndContinue
        )
    }
}

@Composable
private fun TopicsHeader(query: String, onQueryChange: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(OnboardingColors.Background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.topics_title), style = TextStyles.Title2.copy(color = Color.White))
        Text(
            stringResource(R.string.topics_subtitle),
            style = TextStyles.Body3.copy(color = Color.White.copy(alpha = 0.85f))
        )
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = OnboardingColors.Blue)
            Spacer(Modifier.size(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.topics_search_hint), color = Color.Gray, fontSize = 16.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(OnboardingColors.Blue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = TextStyles.Title5.copy(color = Ink),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun TopicCard(category: CategoryRes.Category, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        if (selected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "topicScale"
    )
    val background by animateColorAsState(if (selected) OnboardingColors.Blue else Mist, label = "topicBg")
    val textColor by animateColorAsState(if (selected) Color.White else Ink, label = "topicText")
    Box(
        modifier = Modifier
            .height(112.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) OnboardingColors.Blue else Color(0xFFD6DFF5),
                shape = RoundedCornerShape(20.dp)
            )
            .semantics { this.selected = selected }
            .clickable(role = Role.Checkbox) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(10.dp)
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(_mapIcon[category.id] ?: R.drawable.ic_anonymous),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                CategoryNames.name(category.id, category.name),
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(OnboardingColors.Coral),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun TopicsBottomBar(
    selected: List<CategoryRes.Category>,
    onRemove: (CategoryRes.Category) -> Unit,
    onContinue: () -> Unit,
) {
    val count = selected.size
    val progress by animateFloatAsState((count.coerceAtMost(MIN_TOPICS)) / MIN_TOPICS.toFloat(), label = "topicsProgress")
    Surface(shadowElevation = 12.dp, color = Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (count < MIN_TOPICS) stringResource(R.string.topics_progress, count)
                    else stringResource(R.string.topics_selected, count),
                    style = TextStyles.Title6.copy(color = Ink),
                    modifier = Modifier.weight(1f)
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = if (count >= MIN_TOPICS) OnboardingColors.Coral else OnboardingColors.Blue,
                trackColor = Mist,
                drawStopIndicator = {},
            )
            AnimatedVisibility(
                visible = selected.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selected, key = { it.id }) { category ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Mist)
                                .clickable { onRemove(category) }
                                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                CategoryNames.name(category.id, category.name),
                                fontSize = 13.sp,
                                color = OnboardingColors.Blue,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.size(4.dp))
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = OnboardingColors.Blue, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Button(
                onClick = onContinue,
                enabled = count >= MIN_TOPICS,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnboardingColors.Blue,
                    disabledContainerColor = Mist,
                    disabledContentColor = OnboardingColors.Blue.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    if (count >= MIN_TOPICS) stringResource(R.string.topics_continue)
                    else stringResource(R.string.topics_need_more, MIN_TOPICS - count),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Lowercase without diacritics (Vietnamese đ included), for the topic search and sort. */
private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')
        .replace('Đ', 'D')
        .replace('ł', 'l')
        .replace('Ł', 'L')
        .replace('ı', 'i')
        .lowercase()
        .trim()

private val _mapIcon by lazy {
    mapOf(
        1 to R.drawable.ic_art,
        2 to R.drawable.ic_book,
        3 to R.drawable.ic_design,
        4 to R.drawable.ic_skirt,
        5 to R.drawable.ic_beauty,
        6 to R.drawable.ic_food,
        7 to R.drawable.ic_performing,
        8 to R.drawable.ic_visual,
        9 to R.drawable.ic_business,
        10 to R.drawable.ic_linkedin,
        12 to R.drawable.ic_statistics_100,
        13 to R.drawable.ic_management,
        14 to R.drawable.ic_marketing,
        15 to R.drawable.ic_non_profit,
        16 to R.drawable.ic_comedy,
        17 to R.drawable.ic_interviews,
        18 to R.drawable.ic_idea,
        19 to R.drawable.ic_move_up,
        20 to R.drawable.ic_education,
        21 to R.drawable.ic_my_space,
        22 to R.drawable.ic_signpost,
        23 to R.drawable.ic_english_100,
        24 to R.drawable.ic_learning_100,
        25 to R.drawable.ic_positive_dynamic,
        26 to R.drawable.ic_sci_fi_100,
        27 to R.drawable.ic_drama_100,
        28 to R.drawable.ic_history,
        29 to R.drawable.ic_heart_with_pulse,
        30 to R.drawable.ic_fitness_100,
        31 to R.drawable.ic_change_100,
        32 to R.drawable.ic_medicine_100,
        33 to R.drawable.ic_psychic_100,
        34 to R.drawable.ic_beet_100,
        36 to R.drawable.ic_baby_100,
        37 to R.drawable.ic_family_100,
        38 to R.drawable.ic_parenting_100,
        39 to R.drawable.ic_pets_100,
        40 to R.drawable.ic_elephant_100,
        41 to R.drawable.ic_stories,
        42 to R.drawable.ic_park,
        43 to R.drawable.ic_animation,
        44 to R.drawable.ic_manga,
        45 to R.drawable.ic_automotive_100,
        46 to R.drawable.ic_aviation_100,
        47 to R.drawable.ic_origami_100,
        48 to R.drawable.ic_games_100,
        49 to R.drawable.ic_wind_turbine_100,
        50 to R.drawable.ic_apartment_100,
        51 to R.drawable.ic_oak_tree_100,
        52 to R.drawable.ic_super_mario_100,
        53 to R.drawable.ic_music_100,
        54 to R.drawable.ic_comment,
        55 to R.drawable.ic_news_100,
        56 to R.drawable.ic_daily_100,
        57 to R.drawable.ic_entertainment_100,
        58 to R.drawable.ic_city_hall_100,
        59 to R.drawable.ic_people_100,
        66 to R.drawable.ic_cancer_100,
        67 to R.drawable.ic_test_tube_100,
        68 to R.drawable.ic_night_100,
        69 to R.drawable.ic_benzene_ring_100,
        70 to R.drawable.ic_worldwide_delivery_100,
        71 to R.drawable.ic_life_cycle_100,
        72 to R.drawable.ic_pi_100,
        73 to R.drawable.ic_national_park_100,
        74 to R.drawable.ic_rainwater_catchment_100,
        75 to R.drawable.ic_trigonometry_100,
        76 to R.drawable.ic_social,
        77 to R.drawable.ic_pinterest_100,
        78 to R.drawable.ic_anubis_100,
        79 to R.drawable.ic_documentary_100,
        80 to R.drawable.ic_body_armor_100,
        81 to R.drawable.ic_hacking_100,
        82 to R.drawable.ic_radioactive_100,
        83 to R.drawable.ic_location_100,
        84 to R.drawable.ic_coconut_cocktail_100,
        85 to R.drawable.ic_romance_100,
        86 to R.drawable.ic_hammer_throw_100,
        87 to R.drawable.ic_baseball_100,
        88 to R.drawable.ic_basketball_100,
        89 to R.drawable.ic_bee_100,
        90 to R.drawable.ic_fantasy_100,
        91 to R.drawable.ic_stadium_100,
        92 to R.drawable.ic_sport_bottle_100,
        93 to R.drawable.ic_track_and_field_100,
        94 to R.drawable.ic_football_100,
        95 to R.drawable.ic_track_and_field_100,
        96 to R.drawable.ic_stadium_100,
        97 to R.drawable.ic_outdoor_swimming_pool_100,
        98 to R.drawable.ic_tennis_100,
        99 to R.drawable.ic_boxing_glove_100,
        100 to R.drawable.ic_year_of_tiger_100,
        102 to R.drawable.ic_mac_client_100,
        103 to R.drawable.ic_anonymous,
        104 to R.drawable.ic_netflix_100,
        105 to R.drawable.ic_film_100,
        106 to R.drawable.ic_theater_100,
        107 to R.drawable.ic_reviews_100,
        108 to R.drawable.ic_alps_100,
        109 to R.drawable.ic_light_snow_100,
        110 to R.drawable.ic_chess_100,
        111 to R.drawable.ic_minecraft_logo_100,
        112 to R.drawable.ic_currency_100
    )
}

@Composable
@Preview
fun SelectFavouriteCategoryScreenPreview() {
    SelectFavouriteCategoryScreen(
        listCategory = listOf(
            CategoryRes.Category(55, "News"),
            CategoryRes.Category(9, "Business"),
            CategoryRes.Category(28, "History"),
            CategoryRes.Category(1, "Arts"),
        ),
        listSelected = listOf(CategoryRes.Category(55, "News"))
    )
}
