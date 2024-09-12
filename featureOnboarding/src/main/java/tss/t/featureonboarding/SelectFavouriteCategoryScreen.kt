package tss.t.featureonboarding

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    var isLoading by remember {
        mutableStateOf(listCategory is TSDataState.Loading)
    }
    var listItems by remember {
        mutableStateOf(listOf<CategoryRes.Category>())
    }
    if (listCategory is TSDataState.Success) {
        listItems = (listCategory as TSDataState.Success<CategoryRes>).data.feeds
        isLoading = false
    }
    Log.d("TuanDv", "${listItems.filter { it.isFavourite }}}: ")
    Box {
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
}

@ExperimentalLayoutApi
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
@Preview
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
        containerColor = Color.Transparent,
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
                        .padding(bottom = 16.dp),
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
                items(listCategory.size, key = {
                    listCategory[it].id
                }) { index ->
                    val item = listCategory[index]
                    CategoryItem(
                        listCategory[index], modifier =
                        Modifier.size(rowWidth),
                        onClick = {
                            onItemSelected.invoke(item, index, it)
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                Icons.Rounded.ShoppingCart,
                contentDescription = category.name,
                modifier = Modifier.weight(1f)
            )
            Text(
                category.name, style = TextStyles.Title6,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp, top = 10.dp)
            )
        }
    }
}