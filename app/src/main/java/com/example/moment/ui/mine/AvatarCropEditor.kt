package com.example.moment.ui.mine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.moment.domain.avatar.AvatarCropMath
import com.example.moment.domain.avatar.AvatarCropState
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AvatarCropEditor(
    session: AvatarCropSession,
    busy: Boolean,
    onCancel: () -> Unit,
    onConfirm: (AvatarCropState, Float) -> Unit
) {
    val cropStateHolder = rememberSaveable(
        session.previewPath,
        saver = avatarCropStateSaver
    ) { mutableStateOf(AvatarCropState()) }
    var cropState by cropStateHolder
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var lastDiameter by rememberSaveable(session.previewPath) { mutableFloatStateOf(0f) }
    val cropDiameter = AvatarCropMath.cropDiameter(viewport.width, viewport.height)
    LaunchedEffect(cropDiameter, session.imageWidth, session.imageHeight) {
        if (lastDiameter > 0f && cropDiameter > 0f && abs(lastDiameter - cropDiameter) > 0.5f) {
            cropStateHolder.value = AvatarCropMath.remapDiameter(
                state = cropStateHolder.value,
                oldDiameter = lastDiameter,
                newDiameter = cropDiameter,
                imageWidth = session.imageWidth.toFloat(),
                imageHeight = session.imageHeight.toFloat()
            )
        }
        if (cropDiameter > 0f) lastDiameter = cropDiameter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel, enabled = !busy) {
                Text("取消", color = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "移动和缩放",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.92f)
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = { onConfirm(cropState, cropDiameter) },
                enabled = !busy && cropDiameter > 0f
            ) {
                Text("完成", color = Color.White)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { viewport = it }
                .pointerInput(session.previewPath, viewport) {
                    val diameter = AvatarCropMath.cropDiameter(viewport.width, viewport.height)
                    if (diameter <= 0f) return@pointerInput
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val imageW = session.imageWidth.toFloat()
                        val imageH = session.imageHeight.toFloat()
                        val cropCenter = Offset(viewport.width / 2f, viewport.height / 2f)
                        val zoomed = AvatarCropMath.applyZoom(
                            state = cropStateHolder.value,
                            zoom = zoom,
                            centroidX = centroid.x,
                            centroidY = centroid.y,
                            cropCenterX = cropCenter.x,
                            cropCenterY = cropCenter.y,
                            imageWidth = imageW,
                            imageHeight = imageH,
                            cropDiameter = diameter
                        )
                        cropStateHolder.value = AvatarCropMath.applyPan(
                            state = zoomed,
                            dx = pan.x,
                            dy = pan.y,
                            imageWidth = imageW,
                            imageHeight = imageH,
                            cropDiameter = diameter
                        )
                    }
                }
        ) {
            if (viewport.width > 0 && viewport.height > 0 && cropDiameter > 0f) {
                CropViewport(
                    session = session,
                    cropState = cropState,
                    viewport = viewport,
                    cropDiameter = cropDiameter
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
        }
        Text(
            "拖动或双指缩放图片",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CropViewport(
    session: AvatarCropSession,
    cropState: AvatarCropState,
    viewport: IntSize,
    cropDiameter: Float
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageW = session.imageWidth.toFloat()
    val imageH = session.imageHeight.toFloat()
    val cropCenter = Offset(viewport.width / 2f, viewport.height / 2f)
    val drawScale = AvatarCropMath.displayScale(imageW, imageH, cropDiameter, cropState.scale)
    val (left, top) = AvatarCropMath.imageTopLeft(
        state = cropState,
        imageWidth = imageW,
        imageHeight = imageH,
        cropDiameter = cropDiameter,
        cropCenterX = cropCenter.x,
        cropCenterY = cropCenter.y
    )
    val drawnW = imageW * drawScale
    val drawnH = imageH * drawScale

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(session.previewPath))
            .allowHardware(false)
            .build(),
        contentDescription = null,
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .requiredSize(
                width = with(density) { drawnW.toDp() },
                height = with(density) { drawnH.toDp() }
            ),
        contentScale = ContentScale.FillBounds
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val overlay = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addOval(
                Rect(
                    left = cropCenter.x - cropDiameter / 2f,
                    top = cropCenter.y - cropDiameter / 2f,
                    right = cropCenter.x + cropDiameter / 2f,
                    bottom = cropCenter.y + cropDiameter / 2f
                )
            )
        }
        drawPath(overlay, Color.Black.copy(alpha = 0.58f))
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = cropDiameter / 2f,
            center = cropCenter,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

private val avatarCropStateSaver = listSaver<androidx.compose.runtime.MutableState<AvatarCropState>, Float>(
    save = { listOf(it.value.scale, it.value.offsetX, it.value.offsetY) },
    restore = { mutableStateOf(AvatarCropState(it[0], it[1], it[2])) }
)
