package tss.t.featureonboarding

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import org.json.JSONObject
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.TSDataState
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.animations.skeleton.placeholder
import tss.t.sharedlibrary.ui.widget.TSBadge
import tss.t.sharedlibrary.ui.widget.TSButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectFavouriteCategoryScreen(viewmodel: OnboardingViewModel) {
    val listCategory by viewmodel.categoryState.collectAsState()
    val listSelected by viewmodel.listFavouriteCategory.collectAsState()
    val isLoading by remember(listCategory) {
        mutableStateOf(listCategory is TSDataState.Loading)
    }
    val listItems by remember(listCategory, listSelected) {
        mutableStateOf(
            if (listCategory is TSDataState.Success) {
                (listCategory as TSDataState.Success<CategoryRes>).data.feeds.map { curr ->
                    curr.isFavourite = listSelected.any { curr.id == it.id }
                    curr
                }
            } else {
                emptyList()
            }
        )
    }
    SelectFavouriteCategoryScreen(
        listCategory = listItems,
        isLoading = isLoading,
        onItemSelected = { index, selected ->
            if (selected) {
                viewmodel.putFavouriteItem(this, index)
            } else {
                viewmodel.removeFavouriteItem(this, index)
            }
        },
        onSaveAndContinue = {
            viewmodel.saveFavouriteItem()
        }
    )
}

@ExperimentalLayoutApi
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun SelectFavouriteCategoryScreen(
    listCategory: List<CategoryRes.Category> = listOf(),
    isLoading: Boolean = false,
    onSaveAndContinue: () -> Unit = {},
    onItemSelected: CategoryRes.Category.(Int, Boolean) -> Unit = { _, _ -> },
) {
    val screenSize = LocalConfiguration.current.screenWidthDp
    val rowCount = (screenSize / 150)
    val rowWidth = (screenSize.dp - ((rowCount - 1) * 16).dp - 32.dp) / rowCount
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Select your favorite Categories",
                        style = TextStyles.Title4
                    )
                    Text(
                        "(Select at least 3)",
                        style = TextStyles.SubTitle3,
                    )
                }
            })
        },
        bottomBar = {
            Column(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x10FFFFFF),
                            Color(0x90FFFFFF),
                        )
                    )
                )
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .animateContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listCategory.filter {
                        it.isFavourite
                    }.forEach {
                        TSBadge(
                            it.name,
                            modifier = Modifier.padding(
                                vertical = 8.dp,
                                horizontal = 8.dp
                            ),
                            textStyle = TextStyles.Body3,
                            icon = Icons.Rounded.Clear,
                            iconClick = {
                                onItemSelected(it, listCategory.indexOf(it), !it.isFavourite)
                            },
                            iconSize = 20.dp,
                            color = Colors.Secondary
                        )
                    }
                }
                TSButton(
                    title = "Lưu và tiếp tục", onClick = {
                        onSaveAndContinue()
                    }, modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 58.dp),
                    enable = listCategory.filter {
                        it.isFavourite
                    }.size >= 3
                )
            }
        },
    ) { paddingValues ->
        LazyVerticalGrid(
            GridCells.Fixed(rowCount),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                items(rowCount * 20) {
                    Box(
                        modifier = Modifier
                            .size(rowWidth)
                            .placeholder(
                                true, Colors.PlaceHolder,
                                RoundedCornerShape(20.dp)
                            )
                    )
                }
            } else {
                items(listCategory) { item ->
                    CategoryItem(
                        category = item,
                        modifier = Modifier.size(rowWidth),
                        onClick = {
                            onItemSelected.invoke(item, listCategory.indexOf(item), it)
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.size(58.dp))
                }
            }

        }
    }
}

@Preview
@Composable
private fun CategoryItem(
    category: CategoryRes.Category = CategoryRes.Category(1, "Arts"),
    modifier: Modifier = Modifier,
    onClick: CategoryRes.Category.(Boolean) -> Unit = {}
) {
    var selected by remember {
        mutableStateOf(category.isFavourite)
    }

    val background by animateColorAsState(
        if (selected) Colors.Primary.copy(alpha = 0.1f) else Color.Transparent,
        label = ""
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                2.dp, color = Colors.StrokeColor,
                shape = RoundedCornerShape(20.dp)
            )
            .background(background, RoundedCornerShape(20.dp))
            .clickable {
                selected = !selected
                onClick(category, selected)
            }
    ) {
        Image(
            painter = painterResource(_mapIcon[category.id] ?: R.drawable.ic_anonymous),
            contentDescription = category.name,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 20.dp)
                .size(48.dp)
        )
        Text(
            text = category.name,
            style = TextStyles.Title6,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp, top = 10.dp)
                .align(Alignment.BottomCenter)
        )
    }
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Preview
fun SelectFavouriteCategoryScreenPreview() {
    val list = mutableListOf<CategoryRes.Category>()
    val jsArr = LocalContext.current.assets.open("categories.json")
        .bufferedReader()
        .readText()
        .let {
            JSONObject(it).getJSONArray("Categories")
        }
    val gson = Gson()
    for (i in 0 until jsArr.length()) {
        list.add(
            gson.fromJson(jsArr[i].toString(), CategoryRes.Category::class.java)
        )
    }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = Color.White,
            surface = Color.White
        )
    ) {
        SelectFavouriteCategoryScreen(
            listCategory = list
        )
    }
}