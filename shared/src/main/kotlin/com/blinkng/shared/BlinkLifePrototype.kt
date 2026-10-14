package com.blinkng.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared Compose UI for the Android + Windows BLINK LIFE 3D phase-one playground.
 *
 * Deliberately session-local: no simulated money is written to live BLINK accounts,
 * and no real-money or BLINK Coin payments are available from this prototype.
 */
@Composable
fun BlinkLifePrototype(
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var x by remember { mutableIntStateOf(3) }
    var y by remember { mutableIntStateOf(2) }
    var cash by remember { mutableIntStateOf(500) }
    var completedJobs by remember { mutableIntStateOf(0) }
    var ownsPlant by remember { mutableStateOf(false) }
    var cameraYaw by remember { mutableFloatStateOf(45f) }
    var notice by remember { mutableStateOf("Walk to the café, tech hub or delivery depot to try a job.") }

    val state = BlinkLifeState(x, y, cash, completedJobs, ownsPlant)
    val place = BlinkLifeRules.placeAt(x, y)
    val task = BlinkLifeRules.taskAt(state)

    fun update(next: BlinkLifeState) {
        x = next.x
        y = next.y
        cash = next.gameCash
        completedJobs = next.completedJobs
        ownsPlant = next.ownsDeskPlant
    }

    fun walk(dx: Int, dy: Int) {
        val next = BlinkLifeRules.move(state, dx, dy)
        if (next != state) {
            update(next)
            notice = BlinkLifeRules.placeAt(next.x, next.y)?.let { "Arrived at ${it.label}." }
                ?: "Walking through the city."
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("BLINK LIFE", modifier = Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Black)
            TextButton(onClick = onExit) { Text("Back to Games") }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("TESTLAB • LOCAL PLAYGROUND", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Game Cash (GC) is fictional. No BLINK Coins, real-money purchases, multiplayer, or withdrawals. Progress is only held during this prototype session.",
                    fontSize = 12.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("GAME CASH", fontSize = 10.sp)
                    Text("${state.gameCash} GC", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("JOBS DONE", fontSize = 10.sp)
                    Text("${state.completedJobs}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("Explore the 3D neighbourhood", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BlinkLifeWorld3D(state = state, cameraYawDegrees = cameraYaw)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("3D world • orbit camera", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { cameraYaw = (cameraYaw + 315f) % 360f }) {
                            Text("↶ Rotate", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { cameraYaw = (cameraYaw + 45f) % 360f }) {
                            Text("Rotate ↷", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Text("Campus · Café · Tech Hub · Market · Delivery · Hostel", fontSize = 11.sp)
        Text("Current location: ${place?.label ?: "City street"}", fontWeight = FontWeight.SemiBold)
        Text("Walk using the arrows below. Rotate the 3D camera independently.", fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { walk(0, -1) }, enabled = y > 0, modifier = Modifier.weight(1f)) { Text("↑") }
            OutlinedButton(onClick = { walk(-1, 0) }, enabled = x > 0, modifier = Modifier.weight(1f)) { Text("←") }
            OutlinedButton(onClick = { walk(0, 1) }, enabled = y < BlinkLifeRules.ROWS - 1, modifier = Modifier.weight(1f)) { Text("↓") }
            OutlinedButton(onClick = { walk(1, 0) }, enabled = x < BlinkLifeRules.COLUMNS - 1, modifier = Modifier.weight(1f)) { Text("→") }
        }
        if (task != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(task.question)
                    Text("Correct answer: +${task.payout} GC (prototype)", fontSize = 12.sp)
                    task.choices.forEachIndexed { index, choice ->
                        OutlinedButton(
                            onClick = {
                                val result = BlinkLifeRules.answer(state, index)
                                update(result.state)
                                notice = result.message
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(choice)
                        }
                    }
                }
            }
        } else if (place == BlinkLifePlace.MARKET) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Market · Room decoration", fontWeight = FontWeight.Bold)
                    Text("Desk plant — ${BlinkLifeRules.DESK_PLANT_PRICE} GC")
                    Button(
                        enabled = !state.ownsDeskPlant,
                        onClick = {
                            val result = BlinkLifeRules.buyDeskPlant(state)
                            update(result.state)
                            notice = result.message
                        }
                    ) { Text(if (state.ownsDeskPlant) "Owned" else "Buy desk plant") }
                }
            }
        } else if (place == BlinkLifePlace.HOSTEL) {
            Text(
                if (state.ownsDeskPlant) "Your hostel room has a desk plant. More decoration options are coming."
                else "Your hostel room is ready. Visit the market for a desk plant.",
                fontSize = 13.sp
            )
        } else {
            Text(
                "Explore the city and visit a marked location to unlock an activity.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(notice, modifier = Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
