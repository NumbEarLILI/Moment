package com.example.moment.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.moment.ui.common.FullscreenImageViewer

@Composable
fun DiaryImageGallery(
    imageUris: List<String>,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    thumbnailSize: Dp = 120.dp,
    rowHeight: Dp = 132.dp
) {
    if (imageUris.isEmpty()) return
    var fullscreenStartIndex by remember { mutableIntStateOf(-1) }

    if (fullscreenStartIndex >= 0) {
        FullscreenImageViewer(
            imageUris = imageUris,
            initialPage = fullscreenStartIndex,
            onDismiss = { fullscreenStartIndex = -1 }
        )
    }

    if (showLabel) {
        Text("手帐图片", style = MaterialTheme.typography.titleSmall)
    }
    LazyRow(
        modifier = modifier.height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(imageUris, key = { index, uri -> "$index:$uri" }) { index, uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(thumbnailSize)
                    .width(thumbnailSize)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { fullscreenStartIndex = index }
            )
        }
    }
}
