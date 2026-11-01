package tss.t.podcast.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tss.t.podcast.R
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

/** Public pages of the product site (Firebase Hosting); the listening web app is on Vercel. */
object SitePages {
    const val PRIVACY = "https://podcast-25c93.web.app/privacy"
    const val TERMS = "https://podcast-25c93.web.app/terms"
    const val DELETE_DATA = "https://podcast-25c93.web.app/delete-data"
    const val SUPPORT_EMAIL = "admin@tuandv.me"
}

/**
 * Settings (parity with iOS M4/M6): explicit content, hidden shows, privacy choices, notifications,
 * policies and contact. Opened from the gear on the Discover tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {},
) {
    val context = LocalContext.current
    val allowExplicit by viewModel.allowExplicit.collectAsState()
    val hiddenShows by viewModel.hiddenShows.collectAsState()
    val privacy by viewModel.privacy.collectAsState()
    val liquidGlass by viewModel.liquidGlass.collectAsState()
    val language by viewModel.language.collectAsState()
    var showLanguages by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val activity = androidx.activity.compose.LocalActivity.current
    val locations by viewModel.locations.collectAsState()
    var showLocations by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showLocations) {
        tss.t.podcast.ui.screens.location.PodcastLocationSheet(
            locations = locations,
            onChange = viewModel::setLocations,
            onDismiss = { showLocations = false }
        )
    }
    if (showLanguages) {
        LanguageDialog(
            languages = viewModel.languages,
            selectedTag = language.tag,
            onDismiss = { showLanguages = false },
            onSelect = { tag ->
                showLanguages = false
                if (tag != language.tag) {
                    viewModel.setLanguage(tag)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        tss.t.podcast.AppLocale.apply(tag)
                    } else {
                        activity?.recreate()
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = TextStyles.Title4) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_content)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_explicit),
                    subtitle = stringResource(R.string.settings_explicit_hint),
                    checked = allowExplicit,
                    onChange = viewModel::setAllowExplicit
                )
            }
            item {
                Text(
                    stringResource(R.string.settings_hidden_shows, hiddenShows.size),
                    style = TextStyles.SubTitle2,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            if (hiddenShows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.settings_hidden_shows_empty),
                        style = TextStyles.SubTitle4,
                        color = Colors.Gray60,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else {
                items(hiddenShows, key = { it.feedId }) { show ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = show.image,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            (show.title as String?).orEmpty(),
                            style = TextStyles.SubTitle3,
                            maxLines = 2,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                        TextButton(onClick = { viewModel.unhide(show.feedId) }) {
                            Text(stringResource(R.string.settings_unhide))
                        }
                    }
                }
            }

            item {
                // Local listening history: kept on this device only, cleared from its screen.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onOpenHistory)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_history), style = TextStyles.SubTitle2)
                        Text(stringResource(R.string.settings_history_hint), style = TextStyles.SubTitle4, color = Colors.Gray60)
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Colors.Gray60
                    )
                }
            }

            item { Divider() }
            item { SectionHeader(stringResource(R.string.settings_section_privacy)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_analytics),
                    subtitle = stringResource(R.string.settings_analytics_hint),
                    checked = privacy.analytics,
                    onChange = viewModel::setAnalytics
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_crash),
                    subtitle = stringResource(R.string.settings_crash_hint),
                    checked = privacy.crashReports,
                    onChange = viewModel::setCrashReports
                )
            }
            item {
                LinkRow(stringResource(R.string.settings_privacy_policy)) { context.openUrl(SitePages.PRIVACY) }
            }
            item {
                LinkRow(stringResource(R.string.settings_terms)) { context.openUrl(SitePages.TERMS) }
            }
            item {
                LinkRow(stringResource(R.string.settings_delete_data)) { context.openUrl(SitePages.DELETE_DATA) }
            }

            item { Divider() }
            item { SectionHeader(stringResource(R.string.settings_section_look)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { showLanguages = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_language), style = TextStyles.SubTitle2)
                        Text(stringResource(R.string.settings_language_hint), style = TextStyles.SubTitle4, color = Colors.Gray60)
                    }
                    Text(language.nativeName, style = TextStyles.SubTitle2, color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { showLocations = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_podcast_location), style = TextStyles.SubTitle2)
                        Text(stringResource(R.string.settings_podcast_location_hint), style = TextStyles.SubTitle4, color = Colors.Gray60)
                    }
                    Text(
                        locations.joinToString(" ") { it.flag },
                        style = TextStyles.SubTitle2
                    )
                }
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_liquid_glass),
                    subtitle = stringResource(R.string.settings_liquid_glass_hint),
                    checked = liquidGlass,
                    onChange = viewModel::setLiquidGlass
                )
            }

            item { Divider() }
            item { SectionHeader(stringResource(R.string.settings_section_app)) }
            item {
                LinkRow(stringResource(R.string.settings_notifications)) { context.openNotificationSettings() }
            }
            item {
                LinkRow(stringResource(R.string.settings_feedback)) {
                    context.sendEmail(
                        subject = context.getString(R.string.settings_feedback_subject, context.appVersion())
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_version, context.appVersion()),
                    style = TextStyles.SubTitle4,
                    color = Colors.Gray60,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = TextStyles.Title5,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(top = 12.dp))

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyles.SubTitle2)
            Text(subtitle, style = TextStyles.SubTitle4, color = Colors.Gray60)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = TextStyles.SubTitle2, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = Colors.Gray60,
            modifier = Modifier.size(18.dp)
        )
    }
}

internal fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal fun Context.sendEmail(subject: String, body: String = "") {
    val uri = Uri.parse(
        "mailto:${SitePages.SUPPORT_EMAIL}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
    )
    runCatching {
        startActivity(Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun Context.openNotificationSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun Context.appVersion(): String = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
}.getOrDefault("")

@Composable
private fun LanguageDialog(
    languages: List<tss.t.core.storage.AppLanguage>,
    selectedTag: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            LazyColumn {
                items(languages, key = { it.tag }) { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onSelect(lang.tag) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = lang.tag == selectedTag,
                            onClick = { onSelect(lang.tag) }
                        )
                        Text(lang.nativeName, style = TextStyles.SubTitle2)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
