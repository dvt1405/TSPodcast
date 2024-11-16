package tss.t.podcast.ui.screens.favourite.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tss.t.podcast.R
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedlibrary.ui.widget.TSButton

@Composable
fun EmptyFavouriteWidget(
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(tss.t.sharedresources.R.drawable.empty_favourite),
            contentDescription = null
        )
        Spacer(Modifier.size(12.dp))
        Text(
            stringResource(R.string.empty_favourite_title),
            style = TextStyles.Body4
        )
        Spacer(Modifier.size(12.dp))
        TSButton(
            title = stringResource(R.string.btn_discover_more_title),
            onClick = onClick
        )
    }
}