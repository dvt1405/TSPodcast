package tss.t.sharedlibrary.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import tss.t.sharedlibrary.R

object Fonts {
    val roboto = FontFamily(
        Font(R.font.roboto, FontWeight.Normal),
        Font(R.font.roboto_medium, FontWeight.Medium),
        Font(R.font.roboto_bold, FontWeight.Bold),
        Font(R.font.roboto_light, FontWeight.Light),
        Font(R.font.roboto_thin, FontWeight.Thin),
        Font(R.font.roboto_italic, FontWeight.Normal, FontStyle.Italic),
    )

    val openSans = FontFamily(
        Font(R.font.open_sans_light, FontWeight.Light),
        Font(R.font.open_sans_regular, FontWeight.Normal),
        Font(R.font.open_sans_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.open_sans_medium, FontWeight.Medium),
        Font(R.font.open_sans_semi_bold, FontWeight.SemiBold),
        Font(R.font.open_sans_bold, FontWeight.Bold),
    )
}