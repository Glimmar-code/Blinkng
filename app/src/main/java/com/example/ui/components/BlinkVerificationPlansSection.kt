package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.verification.BlinkVerificationPlans
import com.blinkng.shared.verification.BlinkVerificationTier

/**
 * Purchase quotes only. A paid subscription cannot activate without payment
 * gateway verification and backend webhook reconciliation.
 */
@Composable
fun BlinkVerificationPlansSection(
    selectedTier: BlinkVerificationTier,
    months: Int,
    onTierSelected: (BlinkVerificationTier) -> Unit,
    onMonthsSelected: (Int) -> Unit,
    checkoutReady: Boolean = false,
    renewalAvailable: Boolean = false,
) {
    val quote = BlinkVerificationPlans.quote(selectedTier, months)
    val baseStyle = MaterialTheme.typography.bodySmall
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("CHOOSE YOUR PLAN", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Text("Free features stay free. Paid badges are BLINK status, not proof of real-world identity.",
            style = baseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BlinkVerificationTier.entries.forEach { tier ->
                Card(
                    onClick = { onTierSelected(tier) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        if (selectedTier == tier) 2.dp else 1.dp,
                        if (selectedTier == tier) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    ) {
                        Text(tier.displayName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (tier == BlinkVerificationTier.FREE) "₦0"
                            else "₦${tier.monthlyNgn}/mo",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        listOf(
            Triple("Post, reels, messages", true, true),
            Triple("Verified profile badge", false, true),
            Triple("Apply to be a seller", false, true),
        ).forEach { (name, free, paid) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(name, modifier = Modifier.weight(1.8f), style = baseStyle)
                Text(if (free) "✓" else "—", modifier = Modifier.weight(.5f),
                    textAlign = TextAlign.Center, style = baseStyle)
                Text(if (paid) "✓" else "—", modifier = Modifier.weight(.5f),
                    textAlign = TextAlign.Center, style = baseStyle)
                Text(if (paid) "✓" else "—", modifier = Modifier.weight(.5f),
                    textAlign = TextAlign.Center, style = baseStyle)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (selectedTier != BlinkVerificationTier.FREE) {
            Text("Duration", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BlinkVerificationPlans.durations.forEach { duration ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = months == duration.months,
                        onClick = { onMonthsSelected(duration.months) },
                        label = { Text("${duration.months}m", fontSize = 12.sp) },
                    )
                }
            }
            if (months == 12) {
                Text("Best value · Save 30%", style = baseStyle, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Total: ₦${quote.totalNgn} for ${quote.description}",
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
            if (quote.savingsNgn > 0) {
                Text("Save ₦${quote.savingsNgn} (${quote.discountPercent}% off monthly pricing).",
                    style = baseStyle)
            }
            Text(
                "Automatic renewal",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Off — no recurring payments without your separate, explicit agreement.",
                    style = baseStyle,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = false,
                    enabled = false, // Gate closed until provider supports verified recurring mandates.
                    onCheckedChange = {},
                )
            }
            Text(
                if (renewalAvailable && checkoutReady)
                    "Recurring billing will require a separate confirmation before activation."
                else
                    "Secure subscription checkout and automatic renewals are not active yet. These are price previews only.",
                style = baseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Free plan · ₦0 · No renewal or payment details needed.",
                style = baseStyle, fontWeight = FontWeight.SemiBold)
        }
    }
}
