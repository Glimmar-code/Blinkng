package com.example.shared

import com.blinkng.shared.live.BlinkLiveCampaign
import com.blinkng.shared.live.BlinkLiveCampaignPolicy
import org.junit.Assert.*
import org.junit.Test

class BlinkLiveCampaignPolicyTest {
  private fun card(
      id: String, kind: String="announcement", priority: Int=10, pinned: Boolean=false
  )= BlinkLiveCampaign(id=id,kind=kind,title="Hello",message="BLINK is online",
      priority=priority,pinned=pinned)

  @Test fun prioritizesEmergenciesAndPins() {
    val list=BlinkLiveCampaignPolicy.displayFor("home",listOf(
      card("normal",priority=100),card("emergency",kind="emergency"),
      card("pinned",pinned=true)))
    assertEquals(2,list.size)
    assertTrue(list.map{it.id}.contains("emergency"))
    assertTrue(list.map{it.id}.contains("pinned"))
  }

  @Test fun disallowsExternalActionRoutesAndJavascriptImages() {
    val unsafe=card("1").copy(actionLabel="Pay now",actionRoute="javascript:alert(1)",
        imageUrl="javascript:alert(1)")
    val sanitized=BlinkLiveCampaignPolicy.sanitize(unsafe)!!
    assertNull(sanitized.actionRoute)
    assertNull(sanitized.actionLabel)
    assertNull(sanitized.imageUrl)
  }

  @Test fun supportsDefinedPagesOnlyAndLimitsCrowding() {
    assertTrue(BlinkLiveCampaignPolicy.displayFor("danger-page",listOf(card("1"))).isEmpty())
    assertEquals(2,BlinkLiveCampaignPolicy.displayFor("marketplace",
        listOf(card("1"),card("2"),card("3"))).size)
  }

  @Test fun safelyTruncatesAndRejectsUnknownKind() {
    val long=card("5").copy(title="A".repeat(120),message="B".repeat(600),frequencyHours=1000)
    val safe=BlinkLiveCampaignPolicy.sanitize(long)!!
    assertEquals(80,safe.title.length)
    assertEquals(500,safe.message.length)
    assertEquals(168,safe.frequencyHours)
    assertNull(BlinkLiveCampaignPolicy.sanitize(card("4",kind="malware")))
  }
}
