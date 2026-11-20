package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SharedContact(
    val name: String,
    val phone: String,
    val avatarUrl: String? = null,
    val blinkUserId: String? = null,   // non-null → person is on BLINK, show "Message"
)

/**
 * Contact message body. Use inside a bubble (it picks colors from the outgoing/incoming style).
 * To render it in chat add to ChatContent:  data class Contact(val contact: SharedContact) : ChatContent
 * then in MessageBubble:  is ChatContent.Contact -> BlinkChatContactMessageCard(c.contact, msg.outgoing, ...)
 */
@Composable
fun BlinkChatContactMessageCard(
    contact: SharedContact,
    outgoing: Boolean,
    onMessage: () -> Unit,
    onSave: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalOutgoingBubbleStyle.current
    val fg = if (outgoing) BlinkChatColors.outgoingText(style) else BlinkChatColors.IncomingText
    val sub = if (outgoing) BlinkChatColors.outgoingMeta(style) else BlinkChatColors.Muted
    Column(modifier.width(244.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlinkAvatar(contact.name, contact.avatarUrl, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.name, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(contact.phone, color = sub, fontSize = 14.sp, maxLines = 1)
            }
            Box(
                Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onCall).padding(8.dp)
            ) { Icon(Icons.Filled.Call, "Call", tint = BlinkChatColors.Accent) }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = 0.12f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (contact.blinkUserId != null) ActionText("Message", fg, onMessage, Modifier.weight(1f))
            ActionText(if (contact.blinkUserId != null) "Save" else "Add contact", fg, onSave, Modifier.weight(1f), icon = true)
        }
    }
}

@Composable
private fun ActionText(label: String, fg: Color, onClick: () -> Unit, modifier: Modifier, icon: Boolean = false) {
    Row(
        modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(Icons.Filled.PersonAdd, null, tint = BlinkChatColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = BlinkChatColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
