package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.ui.components.PremiumPullRefreshIndicator
import com.example.ui.components.formatNumber
import com.example.ui.components.shimmerBackground
import com.example.ui.theme.BlinkPink

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun VideoReelsScreen(
    reels: List<FeedPost>,
    currentUsername: String,
    isDark: Boolean,
    onLike: (String) -> Unit,
    onComment: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onBackToPosts: () -> Unit,
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onHomeClick: () -> Unit = onBackToPosts,
    onConnectClick: () -> Unit = {},
    onGameClick: () -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        indicator = {
            PremiumPullRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                darkSurface = true
            )
        }
    ) {
        when {
            reels.isEmpty() && isLoading -> ReelsLoadingSkeleton()
            reels.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = .65f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No live reels yet", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Drag down to check for new reels.",
                            color = Color.White.copy(alpha = .65f),
                            fontSize = 11.sp
                        )
                        TextButton(onClick = onBackToPosts) { Text("Back to Home") }
                    }
                }
            }
            else -> {
                val pager = rememberPagerState(pageCount = { reels.size })
                LaunchedEffect(pager.currentPage, reels.size, hasMore, isLoadingMore) {
                    if (hasMore && !isLoadingMore && pager.currentPage >= (reels.size - 3).coerceAtLeast(0)) {
                        onLoadMore()
                    }
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    VerticalPager(
                        state = pager,
                        key = { index -> reels[index].id },
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { index ->
                        val reel = reels[index]
                        ReelPage(
                            reel = reel,
                            isActive = index == pager.currentPage,
                            isAuthor = reel.author.equals(currentUsername, ignoreCase = true),
                            onLike = onLike,
                            onComment = onComment,
                            onBookmark = onBookmark,
                            onShare = onShare,
                            onDelete = onDelete,
                            onProfileClick = onProfileClick
                        )
                    }
                    Row(
                        Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Following", color = Color.White.copy(alpha = .62f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.width(20.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("For You", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(24.dp).height(2.dp).background(Color.White, CircleShape))
                        }
                    }
                    IconButton(
                        onClick = onBackToPosts,
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReelsLoadingSkeleton() {
    val base = Color(0xFF171717)
    val highlight = Color(0xFF343434)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 130.dp)
                .shimmerBackground(RoundedCornerShape(0.dp), base, highlight)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 100.dp, bottom = 26.dp)
        ) {
            Box(Modifier.width(112.dp).height(14.dp).shimmerBackground(RoundedCornerShape(8.dp), base, highlight))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(.82f).height(11.dp).shimmerBackground(RoundedCornerShape(8.dp), base, highlight))
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth(.58f).height(11.dp).shimmerBackground(RoundedCornerShape(8.dp), base, highlight))
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(5) {
                Box(Modifier.size(42.dp).shimmerBackground(CircleShape, base, highlight))
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
@Composable
private fun ReelPage(
    reel: FeedPost,
    isActive: Boolean,
    isAuthor: Boolean,
    onLike: (String) -> Unit,
    onComment: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
    onProfileClick: (String) -> Unit
) {
    var burst by remember(reel.id){mutableStateOf(false)};LaunchedEffect(burst){if(burst){kotlinx.coroutines.delay(550);burst=false}}
    val heartScale by animateFloatAsState(if(burst)1.35f else 0f,spring(dampingRatio=Spring.DampingRatioMediumBouncy),label="heartBurst")
    Box(Modifier.fillMaxSize().pointerInput(reel.id){detectTapGestures(onDoubleTap={if(!reel.isLiked)onLike(reel.id);burst=true})}){
        val url=reel.videoUrl?.trim();if(!url.isNullOrBlank())ReelVideo(url,isActive)else Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){Text("Video unavailable",color=Color.White)}
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.15f),Color.Transparent,Color.Black.copy(.82f)))))
        if(burst)Icon(Icons.Default.Favorite,null,tint=Color.White,modifier=Modifier.align(Alignment.Center).size(92.dp).scale(heartScale))
        Column(Modifier.align(Alignment.CenterEnd).navigationBarsPadding().padding(end=8.dp,bottom=64.dp),horizontalAlignment=Alignment.CenterHorizontally){
            AsyncImage(
                model = reel.authorAvatar,
                contentDescription = reel.author,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape).clickable { onProfileClick(reel.author) }
            )
            Spacer(Modifier.height(16.dp))
            ReelAction(Icons.Default.Visibility, formatNumber(reel.viewsCount), Color.White.copy(alpha = .9f)) {}
            ReelAction(
                if (reel.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                formatNumber(reel.likes),
                if (reel.isLiked) BlinkPink else Color.White
            ) { onLike(reel.id) }
            ReelAction(Icons.Default.ChatBubble, formatNumber(reel.commentsCount), Color.White) { onComment(reel.id) }
            ReelAction(
                if (reel.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                "Save",
                Color.White
            ) { onBookmark(reel.id) }
            ReelAction(Icons.Default.Share, formatNumber(reel.sharesCount), Color.White) { onShare(reel.id) }
            if (isAuthor) {
                ReelAction(Icons.Default.DeleteOutline, "Delete", Color(0xFFFF6B6B)) { onDelete(reel.id) }
            }
            Spacer(Modifier.height(10.dp))
            SpinningDisc(reel.authorAvatar)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(start=15.dp,end=88.dp,bottom=18.dp)){
            Text("@${reel.author}",color=Color.White,fontWeight=FontWeight.Black,fontSize=14.sp,modifier=Modifier.clickable{onProfileClick(reel.author)})
            if(reel.text.isNotBlank()){Spacer(Modifier.height(6.dp));Text(reel.text,color=Color.White,fontSize=13.sp,maxLines=4)}
            reel.audioTitle?.takeIf{it.isNotBlank()}?.let{Spacer(Modifier.height(8.dp));Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.MusicNote,null,tint=Color.White,modifier=Modifier.size(14.dp));Spacer(Modifier.width(5.dp));Text(it,color=Color.White,fontSize=10.5.sp,maxLines=1)}}
        }
    }
}
@Composable private fun ReelAction(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String,tint:Color,onClick:()->Unit){var pressed by remember{mutableStateOf(false)};val scale by animateFloatAsState(if(pressed).82f else 1f,spring(dampingRatio=Spring.DampingRatioMediumBouncy),label="reelAction");LaunchedEffect(pressed){if(pressed){kotlinx.coroutines.delay(120);pressed=false}};Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(vertical=6.dp)){IconButton(onClick={pressed=true;onClick()},modifier=Modifier.size(48.dp).scale(scale)){Icon(icon,text,tint=tint,modifier=Modifier.size(29.dp))};Text(text,color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Bold)}}
@Composable private fun SpinningDisc(avatar:String){val t=rememberInfiniteTransition(label="disc");val r by t.animateFloat(0f,360f,infiniteRepeatable(tween(6500,easing=LinearEasing)),label="discR");Surface(shape=CircleShape,color=Color(0xFF202020),modifier=Modifier.size(42.dp).rotate(r)){Box(Modifier.padding(7.dp),contentAlignment=Alignment.Center){AsyncImage(model=avatar,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().clip(CircleShape))}}}
@Composable private fun ReelVideo(url:String,isActive:Boolean){
    val context=LocalContext.current;var error by remember(url){mutableStateOf<String?>(null)}
    val player=remember(url){val http=DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(20000).setReadTimeoutMs(30000);ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().apply{repeatMode=Player.REPEAT_MODE_ONE;setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);addListener(object:Player.Listener{override fun onPlayerError(e:PlaybackException){error=e.errorCodeName}});setMediaItem(MediaItem.fromUri(url));prepare()}}
    LaunchedEffect(isActive,player){player.playWhenReady=isActive;if(isActive)player.play()else player.pause()};DisposableEffect(player){onDispose{player.release()}}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){AndroidView(factory={ctx->PlayerView(ctx).apply{useController=false;resizeMode=AspectRatioFrameLayout.RESIZE_MODE_ZOOM;keepScreenOn=true;this.player=player}},update={it.player=player},modifier=Modifier.fillMaxSize());if(error!=null){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Unable to play this reel",color=Color.White,fontWeight=FontWeight.Bold);TextButton(onClick={error=null;player.prepare();if(isActive)player.play()}){Text("Retry")}}}}
}
