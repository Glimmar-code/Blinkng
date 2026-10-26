package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.BlinkStickerLibrary
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.launch
import java.io.File

/**
 * Private, reusable account-scoped sticker tray.
 * Saved PNG stickers are sent via the same authorized photo transport as chat
 * pictures; the tray never publishes or auto-sends a newly selected image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlinkStickerPicker(
    onSend: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var owner by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<File>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var deletionTarget by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) {
        owner = SupabaseService().getCurrentUserId()
        items = owner?.let { BlinkStickerLibrary.saved(context,it) }.orEmpty()
    }
    val creator = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val account = owner ?: return@rememberLauncherForActivityResult
        if (busy) return@rememberLauncherForActivityResult
        busy=true
        scope.launch {
            runCatching { BlinkStickerLibrary.create(context,account,uri) }
                .onSuccess { items = BlinkStickerLibrary.saved(context,account) }
                .onFailure {
                    Toast.makeText(context,it.message ?: "Couldn't create sticker.",
                        Toast.LENGTH_LONG).show()
                }
            busy=false
        }
    }
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal=18.dp)) {
            Text("My Stickers",style=MaterialTheme.typography.titleLarge)
            Text("Only you can see these saved stickers. They stay on this device.",
                style=MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Button(
                    enabled=!busy && !owner.isNullOrBlank() && items.size<BlinkStickerLibrary.MAX_SAVED,
                    onClick={creator.launch("image/*")}
                ) {Text("＋ Create from photo")}
                Spacer(Modifier.width(10.dp))
                Text("${items.size}/${BlinkStickerLibrary.MAX_SAVED}",
                    style=MaterialTheme.typography.labelMedium)
            }
            if (items.isEmpty()) Text(
                "Create a sticker once, then reuse it whenever you chat.",
                modifier=Modifier.padding(vertical=28.dp)
            )
            LazyVerticalGrid(
                columns=GridCells.Fixed(3),
                modifier=Modifier.fillMaxWidth().heightIn(max=395.dp),
                contentPadding=PaddingValues(vertical=12.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                items(items,key={it.name}) {file ->
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        AsyncImage(
                            model=file,contentDescription="Send saved sticker",
                            contentScale=ContentScale.Fit,
                            modifier=Modifier.size(94.dp).clickable {
                                if(!busy) onSend(Uri.fromFile(file))
                            }
                        )
                        TextButton(onClick={deletionTarget=file},enabled=!busy){
                            Text("Remove")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(6.dp))
    }
    deletionTarget?.let { target ->
        AlertDialog(
            onDismissRequest={deletionTarget=null},
            title={Text("Remove saved sticker?")},
            text={Text("This only removes the copy saved on your device.")},
            confirmButton={TextButton(onClick={
                val account=owner
                deletionTarget=null
                if(account!=null)scope.launch {
                    BlinkStickerLibrary.remove(context,account,target)
                    items=BlinkStickerLibrary.saved(context,account)
                }
            }) {Text("Remove")}},
            dismissButton={TextButton(onClick={deletionTarget=null}){Text("Cancel")}}
        )
    }
}
