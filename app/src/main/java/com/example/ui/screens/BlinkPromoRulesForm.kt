package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.BroadcastRules
import com.blinkng.shared.broadcast.PromotionRewardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkPromoRulesForm(rules: BroadcastRules, onChange: (BroadcastRules) -> Unit) {
    val rewardLabels = mapOf(
        PromotionRewardType.COINS to R.string.broadcast_reward_coins,
        PromotionRewardType.FREE_SPIN to R.string.broadcast_reward_free_spin,
        PromotionRewardType.FEE_DISCOUNT to R.string.broadcast_reward_fee_discount,
        PromotionRewardType.BOOST to R.string.broadcast_reward_boost,
    )
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.broadcast_promo_rules), style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = rules.rewardType?.let { stringResource(rewardLabels[it] ?: R.string.broadcast_reward_coins) }.orEmpty(),
                onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.broadcast_reward_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                PromotionRewardType.entries.forEach { type ->
                    DropdownMenuItem(text = { Text(stringResource(rewardLabels.getValue(type))) }, onClick = {
                        onChange(rules.copy(rewardType = type)); expanded = false
                    })
                }
            }
        }
        NumericRuleField(R.string.broadcast_reward_amount, rules.rewardAmount, onChange = { onChange(rules.copy(rewardAmount = it)) })
        NumericRuleField(R.string.broadcast_per_user_limit, rules.perUserLimit, onChange = { onChange(rules.copy(perUserLimit = it)) })
        NumericRuleField(R.string.broadcast_budget, rules.totalBudget, onChange = { onChange(rules.copy(totalBudget = it)) })
        NumericRuleField(R.string.broadcast_min_purchase, rules.minimumPurchase, onChange = { onChange(rules.copy(minimumPurchase = it)) })
        Text(stringResource(R.string.broadcast_channels), style = MaterialTheme.typography.titleSmall)
        val placements = listOf(
            "market" to R.string.broadcast_placement_market,
            "coins" to R.string.broadcast_placement_coins,
            "spin" to R.string.broadcast_placement_spin,
        )
        placements.forEach { (key, label) ->
            val selected = key in rules.placement
            FilterChip(selected = selected, onClick = {
                onChange(rules.copy(placement = if (selected) rules.placement - key else rules.placement + key))
            }, label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun NumericRuleField(label: Int, value: Int?, onChange: (Int?) -> Unit) {
    OutlinedTextField(value = value?.toString().orEmpty(),
        onValueChange = { onChange(it.toIntOrNull()?.coerceAtLeast(0)) },
        label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}
