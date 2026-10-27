package com.blinkng.shared

import org.junit.Assert.*
import org.junit.Test

class BlinkChatMediaSelectionPolicyTest {
    @Test fun adds_in_order_and_removes_without_reordering() {
        var selected = emptyList<String>()
        selected = BlinkChatMediaSelectionPolicy.toggle(selected, "A")
        selected = BlinkChatMediaSelectionPolicy.toggle(selected, "B")
        selected = BlinkChatMediaSelectionPolicy.toggle(selected, "C")
        assertEquals(2, BlinkChatMediaSelectionPolicy.selectionNumber(selected, "B"))
        assertEquals(listOf("A", "C"),
            BlinkChatMediaSelectionPolicy.toggle(selected, "B"))
    }

    @Test fun refuses_eleven_images_but_keeps_existing_selection() {
        val ten = (1..10).map { it.toString() }
        assertEquals(ten, BlinkChatMediaSelectionPolicy.toggle(ten, "11"))
        assertTrue(BlinkChatMediaSelectionPolicy.canContinue(ten))
    }

    @Test fun duplicate_ids_are_not_submitted() {
        assertFalse(BlinkChatMediaSelectionPolicy.canContinue(listOf("A","A")))
        assertEquals(0, BlinkChatMediaSelectionPolicy.selectionNumber(listOf("A"), "B"))
        assertEquals(listOf("A"),
            BlinkChatMediaSelectionPolicy.toggle(listOf("A", "A"), "A"))
    }

    @Test fun reorder_keeps_unique_ids_and_supports_forward_and_backward_moves() {
        val ids = listOf("A", "B", "C")
        assertEquals(listOf("B", "C", "A"),
            BlinkChatMediaSelectionPolicy.move(ids, 0, 2))
        assertEquals(listOf("C", "A", "B"),
            BlinkChatMediaSelectionPolicy.move(ids, 2, 0))
        assertEquals(ids, BlinkChatMediaSelectionPolicy.move(ids, -1, 1))
        assertEquals(ids, BlinkChatMediaSelectionPolicy.move(ids, 0, 100))
        assertEquals(ids, listOf("A","B","C"))
    }

    @Test fun empty_media_ids_not_added() {
        assertEquals(emptyList<String>(),
            BlinkChatMediaSelectionPolicy.toggle(emptyList(), " "))
        assertFalse(BlinkChatMediaSelectionPolicy.canContinue(emptyList()))
    }
}
