package com.example.moment.domain.avatar

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AvatarCropState(
    val scale: Float = AvatarCropMath.MIN_USER_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

data class AvatarCropSourceRect(
    val left: Float,
    val top: Float,
    val size: Float
)

data class AvatarCropPixelRect(
    val x: Int,
    val y: Int,
    val size: Int
)

object AvatarCropMath {
    const val MIN_USER_SCALE = 1f
    const val MAX_USER_SCALE = 4f

    fun coverScale(imageWidth: Float, imageHeight: Float, cropDiameter: Float): Float {
        if (imageWidth <= 0f || imageHeight <= 0f || cropDiameter <= 0f) return 1f
        return max(cropDiameter / imageWidth, cropDiameter / imageHeight)
    }

    fun displayScale(
        imageWidth: Float,
        imageHeight: Float,
        cropDiameter: Float,
        userScale: Float
    ): Float = coverScale(imageWidth, imageHeight, cropDiameter) *
        userScale.coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)

    fun clamp(
        state: AvatarCropState,
        imageWidth: Float,
        imageHeight: Float,
        cropDiameter: Float
    ): AvatarCropState {
        val scale = state.scale.coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)
        val drawScale = displayScale(imageWidth, imageHeight, cropDiameter, scale)
        val drawnW = imageWidth * drawScale
        val drawnH = imageHeight * drawScale
        val maxOffsetX = max(0f, (drawnW - cropDiameter) / 2f)
        val maxOffsetY = max(0f, (drawnH - cropDiameter) / 2f)
        return AvatarCropState(
            scale = scale,
            offsetX = state.offsetX.coerceIn(-maxOffsetX, maxOffsetX),
            offsetY = state.offsetY.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    fun applyPan(
        state: AvatarCropState,
        dx: Float,
        dy: Float,
        imageWidth: Float,
        imageHeight: Float,
        cropDiameter: Float
    ): AvatarCropState = clamp(
        state.copy(offsetX = state.offsetX + dx, offsetY = state.offsetY + dy),
        imageWidth,
        imageHeight,
        cropDiameter
    )

    fun applyZoom(
        state: AvatarCropState,
        zoom: Float,
        centroidX: Float,
        centroidY: Float,
        cropCenterX: Float,
        cropCenterY: Float,
        imageWidth: Float,
        imageHeight: Float,
        cropDiameter: Float
    ): AvatarCropState {
        val oldScale = state.scale.coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)
        val oldDraw = displayScale(imageWidth, imageHeight, cropDiameter, oldScale)
        if (oldDraw <= 0f) {
            return clamp(state, imageWidth, imageHeight, cropDiameter)
        }
        val newScale = (oldScale * zoom).coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)
        val newDraw = displayScale(imageWidth, imageHeight, cropDiameter, newScale)
        val imageLeft = cropCenterX - imageWidth * oldDraw / 2f + state.offsetX
        val imageTop = cropCenterY - imageHeight * oldDraw / 2f + state.offsetY
        val imageX = (centroidX - imageLeft) / oldDraw
        val imageY = (centroidY - imageTop) / oldDraw
        val offsetX = centroidX - cropCenterX + (imageWidth / 2f - imageX) * newDraw
        val offsetY = centroidY - cropCenterY + (imageHeight / 2f - imageY) * newDraw
        return clamp(
            AvatarCropState(scale = newScale, offsetX = offsetX, offsetY = offsetY),
            imageWidth,
            imageHeight,
            cropDiameter
        )
    }

    fun sourceRect(
        state: AvatarCropState,
        imageWidth: Float,
        imageHeight: Float,
        cropDiameter: Float
    ): AvatarCropSourceRect {
        val clamped = clamp(state, imageWidth, imageHeight, cropDiameter)
        val drawScale = displayScale(imageWidth, imageHeight, cropDiameter, clamped.scale)
        val drawnW = imageWidth * drawScale
        val drawnH = imageHeight * drawScale
        val left = (drawnW / 2f - cropDiameter / 2f - clamped.offsetX) / drawScale
        val top = (drawnH / 2f - cropDiameter / 2f - clamped.offsetY) / drawScale
        return AvatarCropSourceRect(
            left = left,
            top = top,
            size = cropDiameter / drawScale
        )
    }

    fun pixelRect(
        state: AvatarCropState,
        imageWidth: Int,
        imageHeight: Int,
        cropDiameter: Float
    ): AvatarCropPixelRect {
        val src = sourceRect(state, imageWidth.toFloat(), imageHeight.toFloat(), cropDiameter)
        val maxSize = min(imageWidth, imageHeight).coerceAtLeast(1)
        var size = src.size.roundToInt().coerceIn(1, maxSize)
        var x = src.left.roundToInt()
        var y = src.top.roundToInt()
        x = x.coerceIn(0, (imageWidth - size).coerceAtLeast(0))
        y = y.coerceIn(0, (imageHeight - size).coerceAtLeast(0))
        size = min(size, min(imageWidth - x, imageHeight - y)).coerceAtLeast(1)
        return AvatarCropPixelRect(x = x, y = y, size = size)
    }

    fun imageTopLeft(
        state: AvatarCropState,
        imageWidth: Float,
        imageHeight: Float,
        cropDiameter: Float,
        cropCenterX: Float,
        cropCenterY: Float
    ): Pair<Float, Float> {
        val clamped = clamp(state, imageWidth, imageHeight, cropDiameter)
        val drawScale = displayScale(imageWidth, imageHeight, cropDiameter, clamped.scale)
        val left = cropCenterX - imageWidth * drawScale / 2f + clamped.offsetX
        val top = cropCenterY - imageHeight * drawScale / 2f + clamped.offsetY
        return left to top
    }
}
