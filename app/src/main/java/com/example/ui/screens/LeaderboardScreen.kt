package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.LeaderboardUser
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.delay

@Composable
fun LeaderboardScreen(users: List<LeaderboardUser>, userProfile: UserProfile = UserProfile(), onProfileClick: (String) -> Unit, isDark: Boolean, onRefresh: () -> Unit = {}) {
    var campusOnly by remember { mutableStateOf(false) }
    val all=remember(users){users.filter{it.username.isNotBlank()}.sortedWith(compareByDescending<LeaderboardUser>{it.points}.thenBy{it.username.lowercase()}).mapIndexed{i,u->u.copy(rank=i+1)}}
    val campusName=userProfile.university.takeUnless{it.isBlank()||it.equals("null",true)}.orEmpty()
    val visible=remember(all,campusOnly,campusName){if(campusOnly&&campusName.isNotBlank())all.filter{it.university.equals(campusName,true)}.mapIndexed{i,u->u.copy(rank=i+1)}else all}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=110.dp)){
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(shape=CircleShape,color=BlinkGold.copy(alpha=.14f)){Icon(Icons.Default.EmojiEvents,null,tint=BlinkGold,modifier=Modifier.padding(10.dp))}
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)){Text("Leaderboard",fontSize=24.sp,fontWeight=FontWeight.Black);Text("Live rankings from real Blink activity",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(onClick=onRefresh){Icon(Icons.Default.Refresh,"Refresh live leaderboard")}
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=!campusOnly,onClick={campusOnly=false},label={Text("World")})
                FilterChip(selected=campusOnly,enabled=campusName.isNotBlank(),onClick={campusOnly=true},label={Text(if(campusName.isBlank())"Campus unavailable" else "My campus")})
                Spacer(Modifier.weight(1f))
                Surface(shape=RoundedCornerShape(100.dp),color=MaterialTheme.colorScheme.primaryContainer){Text("${visible.size} ranked",modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=10.sp,fontWeight=FontWeight.Bold)}
            }
        }
        if(visible.isNotEmpty()){
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom){
                    listOf(1,0,2).forEach{idx->
                        visible.getOrNull(idx)?.let{user->
                            Surface(Modifier.weight(1f).height(if(idx==0)142.dp else 118.dp).clickable{onProfileClick(user.username)},shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,if(idx==0)BlinkGold else MaterialTheme.colorScheme.outlineVariant),color=if(idx==0)BlinkGold.copy(alpha=.09f)else MaterialTheme.colorScheme.surface){
                                Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                    Text(if(idx==0)"👑" else "#${idx+1}",fontWeight=FontWeight.Black)
                                    AsyncImage(model=user.avatar,contentDescription=user.fullName,contentScale=ContentScale.Crop,modifier=Modifier.size(if(idx==0)48.dp else 40.dp).clip(CircleShape))
                                    Text(user.fullName.ifBlank{user.username},fontSize=11.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text("${user.points} pts",fontSize=10.sp,color=BlinkPink,fontWeight=FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        if(visible.isEmpty()){
            item{Box(Modifier.fillMaxWidth().padding(50.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("No live ranking data yet.",fontWeight=FontWeight.Bold);TextButton(onClick=onRefresh){Text("Refresh from Supabase")}}}}
        }else{
            itemsIndexed(visible,key={_,u->u.username}){index,user->
                var shown by remember(user.username){mutableStateOf(false)}
                LaunchedEffect(user.username){delay((index.coerceAtMost(10)*35).toLong());shown=true}
                AnimatedVisibility(visible=shown,enter=fadeIn()+slideInVertically(initialOffsetY={it/3})){
                    val animatedPoints by animateIntAsState(user.points,label="leader_points")
                    Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp).clickable{onProfileClick(user.username)},shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),color=MaterialTheme.colorScheme.surface){
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                            Text("#${index+1}",modifier=Modifier.width(42.dp),fontWeight=FontWeight.Black)
                            AsyncImage(model=user.avatar,contentDescription=user.fullName,contentScale=ContentScale.Crop,modifier=Modifier.size(48.dp).clip(CircleShape))
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)){
                                Row(verticalAlignment=Alignment.CenterVertically){Text(user.fullName.ifBlank{user.username},fontWeight=FontWeight.Bold,maxLines=1);if(user.verificationBadge!=VerificationBadge.NONE){Spacer(Modifier.width(4.dp));Icon(Icons.Default.Verified,null,tint=BlinkPink,modifier=Modifier.size(14.dp))}}
                                Text("@${user.username}",fontSize=10.5.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                user.university.takeUnless{it.isBlank()||it.equals("null",true)}?.let{Text(it,fontSize=9.5.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}
                            }
                            Column(horizontalAlignment=Alignment.End){Text("$animatedPoints",fontWeight=FontWeight.Black,color=BlinkPink);Text("points",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                    }
                }
            }
        }
    }
}
