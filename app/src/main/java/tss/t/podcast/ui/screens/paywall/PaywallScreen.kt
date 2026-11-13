package tss.t.podcast.ui.screens.paywall

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tss.t.billing.Entitlement
import tss.t.billing.IapProduct
import tss.t.billing.IapProductType
import tss.t.billing.PaywallProduct
import tss.t.billing.ProductsState
import tss.t.podcast.R
import tss.t.podcast.ui.screens.settings.SitePages
import tss.t.podcast.ui.screens.settings.openUrl
import tss.t.sharedlibrary.theme.Colors
import tss.t.sharedlibrary.theme.TextStyles

/**
 * Premium paywall: benefits, the plans the store returned (ordered by `order`, localised price
 * and period from Play, badge, free trial only when Play offers an eligible one), buy, restore,
 * Terms / Privacy and the auto-renew disclosure (Play subscription policy). When the user is
 * already Premium it shows the active state and "Manage subscription".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    viewModel: PaywallViewModel,
    source: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val products by viewModel.products.collectAsState()
    val entitlement by viewModel.entitlement.collectAsState()
    val picked by viewModel.selected.collectAsState()
    val restoring by viewModel.restoring.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.onShown(source) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(context.getString(message.text()))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paywall_title), style = TextStyles.Title4) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Header()
            if (entitlement.premium) {
                ActiveState(entitlement, onManage = { context.openManageSubscription(entitlement) })
            } else {
                Benefits()
                if (entitlement.pendingProductIds.isNotEmpty()) PendingNotice()
                when (val state = products) {
                    ProductsState.Loading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    is ProductsState.Unavailable -> Unavailable(onRetry = viewModel::retry)

                    is ProductsState.Ready -> {
                        val selected = viewModel.preselect(state.products, picked)
                        state.products.forEach { item ->
                            PlanRow(
                                item = item,
                                selected = item.product.id == selected?.product?.id,
                                onSelect = { viewModel.select(item.product.id) }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (activity != null && selected != null) viewModel.buy(activity, selected, source)
                            },
                            enabled = selected != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    if (selected?.freeTrialPeriod != null) R.string.paywall_start_trial else R.string.paywall_buy
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.restore(source) },
                    enabled = !restoring,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.paywall_restore))
                }
                Text(
                    stringResource(R.string.paywall_auto_renew),
                    style = TextStyles.SubTitle4,
                    color = Colors.Gray60,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { context.openUrl(SitePages.TERMS) }) {
                    Text(stringResource(R.string.settings_terms), style = TextStyles.SubTitle4)
                }
                TextButton(onClick = { context.openUrl(SitePages.PRIVACY) }) {
                    Text(stringResource(R.string.settings_privacy_policy), style = TextStyles.SubTitle4)
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.WorkspacePremium,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.paywall_headline), style = TextStyles.Title3, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.paywall_subtitle),
            style = TextStyles.SubTitle3,
            color = Colors.Gray60,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun Benefits() {
    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Benefit(Icons.Rounded.Block, stringResource(R.string.paywall_benefit_no_ads))
        Benefit(Icons.Rounded.Devices, stringResource(R.string.paywall_benefit_restore))
        Benefit(Icons.Rounded.Favorite, stringResource(R.string.paywall_benefit_support))
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(text, style = TextStyles.SubTitle2, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun PlanRow(item: PaywallProduct, selected: Boolean, onSelect: () -> Unit) {
    val period = BillingPeriod.parse(item.billingPeriod)
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else Colors.Gray30
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(planName(item.product, period), style = TextStyles.SubTitle1)
                    if (item.product.badge == IapProduct.BADGE_BEST_VALUE) {
                        Text(
                            stringResource(R.string.paywall_badge_best_value),
                            style = TextStyles.Caption2,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(priceText(item.formattedPrice, period), style = TextStyles.SubTitle2, color = Colors.Gray60)
                if (item.product.type == IapProductType.OneTime) {
                    Text(stringResource(R.string.paywall_plan_lifetime_hint), style = TextStyles.SubTitle4, color = Colors.Gray60)
                }
                val trial = BillingPeriod.parse(item.freeTrialPeriod)
                if (trial != null) {
                    Text(
                        stringResource(R.string.paywall_trial, durationText(trial)),
                        style = TextStyles.SubTitle4,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun planName(product: IapProduct, period: BillingPeriod?): String = when {
    product.type == IapProductType.OneTime -> stringResource(R.string.paywall_plan_lifetime)
    period == BillingPeriod(1, BillingPeriod.Unit.Month) -> stringResource(R.string.paywall_plan_monthly)
    period == BillingPeriod(1, BillingPeriod.Unit.Year) -> stringResource(R.string.paywall_plan_yearly)
    period != null -> durationText(period)
    else -> stringResource(R.string.paywall_title)
}

@Composable
private fun priceText(price: String, period: BillingPeriod?): String = when {
    period == null -> price
    period.count == 1 -> stringResource(
        when (period.unit) {
            BillingPeriod.Unit.Day -> R.string.paywall_price_per_day
            BillingPeriod.Unit.Week -> R.string.paywall_price_per_week
            BillingPeriod.Unit.Month -> R.string.paywall_price_per_month
            BillingPeriod.Unit.Year -> R.string.paywall_price_per_year
        },
        price
    )

    else -> stringResource(R.string.paywall_price_every, price, durationText(period))
}

@Composable
private fun durationText(period: BillingPeriod): String = pluralStringResource(
    when (period.unit) {
        BillingPeriod.Unit.Day -> R.plurals.paywall_duration_days
        BillingPeriod.Unit.Week -> R.plurals.paywall_duration_weeks
        BillingPeriod.Unit.Month -> R.plurals.paywall_duration_months
        BillingPeriod.Unit.Year -> R.plurals.paywall_duration_years
    },
    period.count,
    period.count
)

@Composable
private fun PendingNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(Colors.Yellow10, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = Colors.Yellow50)
        Text(
            stringResource(R.string.paywall_pending),
            style = TextStyles.SubTitle3,
            color = Colors.Yellow70,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun Unavailable(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.paywall_unavailable),
            style = TextStyles.SubTitle3,
            color = Colors.Gray60,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.paywall_retry))
        }
    }
}

@Composable
private fun ActiveState(entitlement: Entitlement, onManage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.paywall_active_title), style = TextStyles.Title5, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.paywall_active_message),
            style = TextStyles.SubTitle3,
            color = Colors.Gray60,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (entitlement.subscriptionStoreProductId != null) {
            OutlinedButton(onClick = onManage, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.paywall_manage))
            }
        }
    }
}

private fun PaywallMessage.text(): Int = when (this) {
    PaywallMessage.Restored -> R.string.paywall_restored
    PaywallMessage.NothingToRestore -> R.string.paywall_restore_none
    PaywallMessage.RestoreFailed -> R.string.paywall_restore_failed
    PaywallMessage.PurchaseFailed -> R.string.paywall_purchase_failed
    PaywallMessage.Pending -> R.string.paywall_pending
    PaywallMessage.Unavailable -> R.string.paywall_unavailable
}

/** Play's subscription page for the owned Premium subscription (contract §3). */
internal fun Context.openManageSubscription(entitlement: Entitlement) {
    val sku = entitlement.subscriptionStoreProductId
    openUrl(
        if (sku == null) {
            "https://play.google.com/store/account/subscriptions"
        } else {
            "https://play.google.com/store/account/subscriptions?sku=$sku&package=$packageName"
        }
    )
}
