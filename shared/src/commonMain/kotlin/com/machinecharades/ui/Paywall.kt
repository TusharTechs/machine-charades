package com.machinecharades.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.machinecharades.data.Plan

/**
 * App Review guideline 3.1.2 requires an app selling auto-renewable
 * subscriptions to carry the title, length, price, and functional links to
 * both the terms and the privacy policy *inside the binary* — the store
 * listing alone is not enough. The first three were already here; these are
 * the links.
 */
private const val TERMS_URL =
    "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"
private const val PRIVACY_URL =
    "https://tushartechs.github.io/machine-charades/privacy.html"

/**
 * The Plus paywall.
 *
 * Written by hand rather than using RevenueCat's own Paywalls component: the
 * game commits to one fixed dark palette and the reveal colours carry meaning,
 * so a remotely themed sheet would arrive as a different product. The SDK still
 * owns products, prices and the purchase itself — only the pixels are local.
 *
 * Prices come from [Plan.price], which the store has already localised. Never
 * compose a price string in the app; it will be wrong in most of the world.
 */
@Composable
fun Paywall(
    plans: List<Plan>,
    busy: Boolean,
    error: String?,
    onBuy: (Plan) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeContentPadding().fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onDismiss) { Text("Not now") }
            }

            Text(
                "MACHINE CHARADES",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Plus",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MachineGreen,
            )
            Text(
                "Today's puzzle is always free. Plus is for the days you want it harder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Perk("Every constraint mode, now", "No vowels. One word. Twenty characters. The three hardest ways to write a clue.")
                Perk("The full archive", "Play any puzzle you have missed, all the way back to the first.")
                Perk("Your whole record", "Streak history, solve rate, and how short your clues really are.")
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            if (plans.isEmpty()) {
                // Offerings not loaded. Say so plainly rather than showing an
                // empty space where the prices should be.
                Text(
                    if (busy) "Loading plans…" else "Plans are unavailable right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Annual first: it is the better deal and the one most
                    // players should take.
                    plans.sortedByDescending { it.isAnnual }.forEach { plan ->
                        PlanButton(plan, busy) { onBuy(plan) }
                    }
                }
            }

            TextButton(onRestore, enabled = !busy) { Text("Restore a purchase") }

            Text(
                "Cancel any time. A free trial converts only if you keep it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            val uriHandler = LocalUriHandler.current
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ uriHandler.openUri(TERMS_URL) }) {
                    Text("Terms of Use", style = MaterialTheme.typography.bodySmall)
                }
                TextButton({ uriHandler.openUri(PRIVACY_URL) }) {
                    Text("Privacy Policy", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Perk(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Drawn, not the 🟩 emoji. An emoji renders differently on every
        // platform and OS version, and this one is already spoken for: on the
        // round screen a green square means the machine got it. Spending that
        // mark on bullet points cheapens it in the place it matters.
        //
        // The shape is the app icon's tile, at bullet size.
        Box(
            Modifier.padding(top = 5.dp)
                .size(10.dp)
                .background(MachineGreen, RoundedCornerShape(3.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanButton(plan: Plan, busy: Boolean, onClick: () -> Unit) {
    if (plan.isAnnual) {
        Button(
            onClick = onClick,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("${plan.price} a year", fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(999.dp),
        ) { Text("${plan.price} a month") }
    }
}
