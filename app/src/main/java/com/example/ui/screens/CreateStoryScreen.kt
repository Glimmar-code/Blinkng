package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple

@Composable
fun CreateStoryScreen(profile:UserProfile,isUploading:Boolean,onBack:()->Unit,onPublish:(String,String,Boolean)->Unit){
    var selectedUri by rememberSaveable{mutableStateOf<String?>(null)};var isVideo by rememberSaveable{mutableStateOf(false)};var caption by rememberSaveable{mutableStateOf("")}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let{selectedUri=it.toString();isVideo=false}}
    val videoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let{selectedUri=it.toString();isVideo=true}}
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background,BlinkPurple.copy(alpha=.10f),BlinkPink.copy(alpha=.06f))))){
        Column(Modifier.fillMaxSize().systemBarsPadding()){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onBack,enabled=!isUploading){Icon(Icons.Default.ArrowBack,"Back")}
                Text("Add Story",style=MaterialTheme.typography.titleLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.Black,modifier=Modifier.weight(1f))
                Button(onClick={selectedUri?.let{onPublish(it,caption.trim(),isVideo)}},enabled=selectedUri!=null&&!isUploading,shape=RoundedCornerShape(100.dp)){if(isUploading)CircularProgressIndicator(modifier=Modifier.size(18.dp),strokeWidth=2.dp)else Text("Share")}
            }
            Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(model=profile.avatarUrl,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.size(40.dp).clip(CircleShape));Spacer(Modifier.width(9.dp));Column{Text(profile.fullName.ifBlank{profile.username},fontWeight=androidx.compose.ui.text.font.FontWeight.Bold);Text("@${profile.username} • disappears after 24h",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            Spacer(Modifier.height(14.dp))
            Surface(Modifier.fillMaxWidth().weight(1f).padding(horizontal=16.dp),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=4.dp){
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                    val uri=selectedUri
                    if(uri==null){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Create a new story",style=MaterialTheme.typography.titleMedium,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold);Spacer(Modifier.height(6.dp));Text("Choose a photo or video from your device",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(20.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){StoryMediaChoice(Icons.Default.Image,"Photo"){imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))};StoryMediaChoice(Icons.Default.VideoLibrary,"Video"){videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))}}}}
                    else{
                        Box(Modifier.fillMaxSize()){
                            androidx.compose.animation.AnimatedVisibility(
                                visible = true,
                                modifier = Modifier.fillMaxSize(),
                                enter = fadeIn() + scaleIn()
                            ){
                                if(!isVideo) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Story preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ){
                                        Icon(Icons.Default.VideoLibrary,null,tint=BlinkPink,modifier=Modifier.size(64.dp))
                                        Spacer(Modifier.height(10.dp))
                                        Text("Video selected",fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                                        Text("It will upload securely to Supabase",color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            TextButton(
                                onClick={selectedUri=null},
                                enabled=!isUploading,
                                modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)
                            ){Text("Change")}
                        }
                    }
                }
            }
            OutlinedTextField(value=caption,onValueChange={caption=it.take(500)},modifier=Modifier.fillMaxWidth().padding(16.dp),placeholder={Text("Add a caption…")},enabled=!isUploading,minLines=2,maxLines=4,shape=RoundedCornerShape(20.dp))
        }
    }
}
@Composable private fun StoryMediaChoice(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,onClick:()->Unit){Surface(Modifier.width(120.dp).clickable(onClick=onClick),shape=RoundedCornerShape(20.dp),color=BlinkPink.copy(alpha=.10f)){Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=BlinkPink);Spacer(Modifier.height(6.dp));Text(label,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)}}}
