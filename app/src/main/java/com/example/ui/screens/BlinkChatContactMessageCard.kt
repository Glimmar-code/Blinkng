package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkChatContactCard
import com.example.ui.theme.MessagePalette

/** System-mediated actions; BLINK never silently edits the recipient's address book. */
@Composable
internal fun BlinkChatContactMessageCard(
    contact: BlinkChatContactCard,
    palette: MessagePalette,
    textColor: Color,
) {
    val context = LocalContext.current
    Surface(
        color = palette.glass.copy(alpha = .5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person, contentDescription = null,
                    tint = textColor, modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        contact.displayName,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    Text(contact.phoneNumber, color = textColor.copy(alpha = .8f), fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        type = ContactsContract.Contacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.NAME, contact.displayName)
                        putExtra(ContactsContract.Intents.Insert.PHONE, contact.phoneNumber)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No contact app available", Toast.LENGTH_SHORT).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, "Unable to open contacts", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Save contact", color = textColor, fontSize = 12.sp) }
                TextButton(onClick = {
                    val intent = Intent(
                        Intent.ACTION_DIAL,
                        Uri.fromParts("tel", contact.phoneNumber, null)
                    )
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No phone app available", Toast.LENGTH_SHORT).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, "Unable to open dialer", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Dial", color = textColor, fontSize = 12.sp) }
            }
        }
    }
}
