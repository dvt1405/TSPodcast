package tss.t.podcast.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tss.t.podcast.R
import tss.t.sharedfirebase.PrivacyConsent
import tss.t.sharedlibrary.theme.TextStyles

/**
 * First-launch privacy question (iOS M6 N11 lite): usage analytics and crash reports stay off
 * until the user picks. Both buttons have the same size and weight, and the sheet does not block
 * the app behind it; dismissing it keeps everything off and asks again next launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyConsentSheet(consent: PrivacyConsent) {
    val choices by consent.choices.collectAsState()
    if (choices.decided) return
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.consent_title), style = TextStyles.Title4)
            Text(stringResource(R.string.consent_body), style = TextStyles.SubTitle3)
            Text(stringResource(R.string.consent_necessary_note), style = TextStyles.SubTitle4)
            TextButton(onClick = { context.openUrl(SitePages.PRIVACY) }) {
                Text(stringResource(R.string.settings_privacy_policy))
            }
            Button(onClick = consent::acceptAll, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.consent_accept_all))
            }
            OutlinedButton(onClick = consent::necessaryOnly, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.consent_necessary_only))
            }
        }
    }
}
