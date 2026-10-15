package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Static skeleton gradient: no infinite animation for each visible cell. */
@Composable
fun BlinkSkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.shimmerBackground(
        shape = shape,
        baseColor = colors.surfaceVariant.copy(alpha = 0.85f),
        highlightColor = colors.onSurface.copy(alpha = 0.10f)
    ))
}

@Composable
fun BlinkSkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    minRowHeight: Dp = 76.dp,
    avatar: Boolean = true
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        repeat(BlinkSkeletonPolicy.placeholderRows(rows)) { index ->
            Row(
                modifier = Modifier.fillMaxWidth().height(minRowHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (avatar) BlinkSkeletonBlock(Modifier.size(44.dp), CircleShape)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlinkSkeletonBlock(Modifier.fillMaxWidth(if (index % 2 == 0) 0.55f else 0.72f).height(13.dp))
                    BlinkSkeletonBlock(Modifier.fillMaxWidth(0.92f).height(10.dp))
                    BlinkSkeletonBlock(Modifier.fillMaxWidth(0.63f).height(10.dp))
                }
            }
        }
    }
}

@Composable
fun BlinkSkeletonChatHistory(modifier: Modifier = Modifier, rows: Int = 6) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)) {
        repeat(BlinkSkeletonPolicy.placeholderRows(rows)) { index ->
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = if (index % 3 == 1) Arrangement.End else Arrangement.Start) {
                BlinkSkeletonBlock(
                    Modifier.width(if (index % 3 == 1) 198.dp else 232.dp)
                        .height(if (index % 2 == 0) 58.dp else 43.dp),
                    RoundedCornerShape(18.dp)
                )
            }
        }
    }
}

@Composable
fun BlinkSkeletonMarketRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BlinkSkeletonBlock(Modifier.fillMaxWidth().height(155.dp), RoundedCornerShape(16.dp))
                BlinkSkeletonBlock(Modifier.fillMaxWidth(0.82f).height(12.dp))
                BlinkSkeletonBlock(Modifier.fillMaxWidth(0.56f).height(11.dp))
            }
        }
    }
}

@Composable
fun BlinkSkeletonTargetRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BlinkSkeletonBlock(Modifier.fillMaxWidth().height(122.dp), RoundedCornerShape(16.dp))
                BlinkSkeletonBlock(Modifier.fillMaxWidth(0.76f).height(12.dp))
            }
        }
    }
}

@Composable
fun BlinkSkeletonMetricStrip(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) {
            BlinkSkeletonBlock(Modifier.weight(1f).height(56.dp), RoundedCornerShape(14.dp))
        }
    }
}
