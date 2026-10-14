package com.blinkng.shared

/**
 * BLINK LIFE Testlab-only, local sandbox. Game Cash is fictional and is never
 * BLINK Coins, Nigerian naira, a payment balance, or a redeemable reward.
 *
 * Keep all rules here so Android and Windows execute identical gameplay.
 * Production wallets, profiles, and rewards must eventually be server-authoritative.
 */
enum class BlinkLifePlace(val x: Int, val y: Int, val label: String, val symbol: String) {
    CAMPUS(3, 0, "Campus", "U"),
    CAFE(0, 1, "Café", "C"),
    TECH_HUB(6, 1, "Tech hub", "T"),
    MARKET(0, 3, "Market", "M"),
    DELIVERY_DEPOT(2, 4, "Delivery depot", "D"),
    HOSTEL(6, 4, "Hostel", "H")
}

data class BlinkLifeState(
    val x: Int = 3,
    val y: Int = 2,
    val gameCash: Int = 500,
    val completedJobs: Int = 0,
    val ownsDeskPlant: Boolean = false
)

data class BlinkLifeTask(
    val title: String,
    val question: String,
    val choices: List<String>,
    val correctChoice: Int,
    val payout: Int
)

data class BlinkLifeResult(
    val state: BlinkLifeState,
    val message: String,
    val succeeded: Boolean
)

object BlinkLifeRules {
    const val COLUMNS = 7
    const val ROWS = 5
    const val DESK_PLANT_PRICE = 250

    fun placeAt(x: Int, y: Int): BlinkLifePlace? =
        BlinkLifePlace.entries.firstOrNull { it.x == x && it.y == y }

    /** Only one orthogonal tile per action. Out-of-bounds/teleport moves are ignored. */
    fun move(state: BlinkLifeState, dx: Int, dy: Int): BlinkLifeState {
        if (kotlin.math.abs(dx.toLong()) + kotlin.math.abs(dy.toLong()) != 1L) return state
        val newX = state.x + dx
        val newY = state.y + dy
        return if (newX in 0 until COLUMNS && newY in 0 until ROWS) {
            state.copy(x = newX, y = newY)
        } else state
    }

    fun taskAt(state: BlinkLifeState): BlinkLifeTask? = when (placeAt(state.x, state.y)) {
        BlinkLifePlace.CAFE -> BlinkLifeTask(
            title = "Café cashier",
            question = "Two snacks cost 80 GC each. A customer pays 200 GC. What is the change?",
            choices = listOf("20 GC", "40 GC", "60 GC"),
            correctChoice = 1,
            payout = 120
        )
        BlinkLifePlace.TECH_HUB -> BlinkLifeTask(
            title = "Tech support",
            question = "A laptop will not charge. What should you check first?",
            choices = listOf("Power cable and outlet", "Delete photos", "Mute the speakers"),
            correctChoice = 0,
            payout = 180
        )
        BlinkLifePlace.DELIVERY_DEPOT -> BlinkLifeTask(
            title = "Delivery dispatch",
            question = "Which action makes a delivery safer?",
            choices = listOf("Ignore directions", "Follow the marked route", "Speed through traffic"),
            correctChoice = 1,
            payout = 150
        )
        else -> null
    }

    /** A local demonstration only; never use client-submitted results for actual coin payouts. */
    fun answer(state: BlinkLifeState, choice: Int): BlinkLifeResult {
        val task = taskAt(state)
            ?: return BlinkLifeResult(state, "Visit a workplace to accept a task.", false)
        if (choice !in task.choices.indices) {
            return BlinkLifeResult(state, "Select a valid answer.", false)
        }
        if (choice != task.correctChoice) {
            return BlinkLifeResult(state, "Not quite. Try another answer.", false)
        }
        val updated = state.copy(
            gameCash = (state.gameCash.toLong() + task.payout).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            completedJobs = (state.completedJobs.toLong() + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
        return BlinkLifeResult(updated, "Task complete! +${task.payout} fictional Game Cash.", true)
    }

    fun buyDeskPlant(state: BlinkLifeState): BlinkLifeResult {
        if (placeAt(state.x, state.y) != BlinkLifePlace.MARKET) {
            return BlinkLifeResult(state, "Visit the market to shop.", false)
        }
        if (state.ownsDeskPlant) {
            return BlinkLifeResult(state, "You already own the desk plant.", false)
        }
        if (state.gameCash < DESK_PLANT_PRICE) {
            return BlinkLifeResult(state, "Not enough Game Cash for the plant.", false)
        }
        return BlinkLifeResult(
            state.copy(gameCash = state.gameCash - DESK_PLANT_PRICE, ownsDeskPlant = true),
            "Desk plant purchased! Visit your hostel to see it.",
            true
        )
    }
}
