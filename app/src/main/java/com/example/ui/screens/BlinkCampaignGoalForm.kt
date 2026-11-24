package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.BroadcastRewardTier
import com.blinkng.shared.broadcast.BroadcastRules
import com.blinkng.shared.broadcast.CampaignGoalType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkCampaignGoalForm(rules: BroadcastRules, onChange: (BroadcastRules) -> Unit) {
    val goalLabels = mapOf(
        CampaignGoalType.INVITES to R.string.broadcast_goal_invites,
        CampaignGoalType.PURCHASES to R.string.broadcast_goal_purchases,
        CampaignGoalType.ORDERS to R.string.broadcast_goal_orders,
        CampaignGoalType.VOTES to R.string.broadcast_goal_votes,
    )
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.broadcast_campaign_goal), style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = rules.goalType?.let { stringResource(goalLabels[it] ?: R.string.broadcast_goal_invites) }.orEmpty(),
                onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.broadcast_goal_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                CampaignGoalType.entries.forEach { goal ->
                    DropdownMenuItem(text = { Text(stringResource(goalLabels.getValue(goal))) }, onClick = {
                        onChange(rules.copy(goalType = goal)); expanded = false
                    })
                }
            }
        }
        OutlinedTextField(value = rules.target?.toString().orEmpty(),
            onValueChange = { onChange(rules.copy(target = it.toIntOrNull()?.coerceAtLeast(1))) },
            label = { Text(stringResource(R.string.broadcast_target)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(stringResource(R.string.broadcast_reward_tiers), style = MaterialTheme.typography.titleSmall)
        rules.rewardTiers.forEachIndexed { index, tier ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(tier.threshold.toString(), {
                    onChange(rules.copy(rewardTiers = rules.rewardTiers.toMutableList().apply {
                        this[index] = tier.copy(threshold = it.toIntOrNull()?.coerceAtLeast(1) ?: tier.threshold)
                    }))
                }, label = { Text(stringResource(R.string.broadcast_target)) }, modifier = Modifier.weight(.28f), singleLine = true)
                OutlinedTextField(tier.amount.toString(), {
                    onChange(rules.copy(rewardTiers = rules.rewardTiers.toMutableList().apply {
                        this[index] = tier.copy(amount = it.toIntOrNull()?.coerceAtLeast(0) ?: tier.amount)
                    }))
                }, label = { Text("Coins") }, modifier = Modifier.weight(.28f), singleLine = true)
                OutlinedTextField(tier.reward, {
                    onChange(rules.copy(rewardTiers = rules.rewardTiers.toMutableList().apply {
                        this[index] = tier.copy(reward = it.take(100))
                    }))
                }, label = { Text(stringResource(R.string.broadcast_reward)) }, modifier = Modifier.weight(.44f), singleLine = true)
            }
        }
        TextButton(onClick = {
            onChange(rules.copy(rewardTiers = rules.rewardTiers + BroadcastRewardTier(
                threshold = (rules.rewardTiers.lastOrNull()?.threshold ?: 0) + 1, reward = "", amount = 0
            )))
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.broadcast_add_tier)) }
    }
}
