package com.example.moment.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarCropMathTest {
    @Test
    fun coverScaleFillsTheCircleOnTheShorterImageEdge() {
        assertEquals(0.5f, AvatarCropMath.coverScale(400f, 200f, 100f))
        assertEquals(1f, AvatarCropMath.coverScale(100f, 200f, 100f))
        assertEquals(2f, AvatarCropMath.coverScale(50f, 50f, 100f))
    }

    @Test
    fun defaultStateCropsTheCenteredCoverSquare() {
        val rect = AvatarCropMath.sourceRect(
            state = AvatarCropState(),
            imageWidth = 400f,
            imageHeight = 200f,
            cropDiameter = 100f
        )

        assertEquals(100f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(200f, rect.size, 0.01f)
    }

    @Test
    fun panningMovesTheSourceRectAndStaysInsideTheImage() {
        val panned = AvatarCropMath.applyPan(
            state = AvatarCropState(),
            dx = 10_000f,
            dy = 10_000f,
            imageWidth = 400f,
            imageHeight = 200f,
            cropDiameter = 100f
        )
        val rect = AvatarCropMath.sourceRect(
            state = panned,
            imageWidth = 400f,
            imageHeight = 200f,
            cropDiameter = 100f
        )

        assertEquals(0f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(200f, rect.size, 0.01f)
        assertEquals(50f, panned.offsetX, 0.01f)
        assertEquals(0f, panned.offsetY, 0.01f)
    }

    @Test
    fun zoomingInShrinksTheSourceSquareAroundTheCircleCenter() {
        val zoomed = AvatarCropMath.applyZoom(
            state = AvatarCropState(),
            zoom = 2f,
            centroidX = 50f,
            centroidY = 50f,
            cropCenterX = 50f,
            cropCenterY = 50f,
            imageWidth = 400f,
            imageHeight = 200f,
            cropDiameter = 100f
        )
        val rect = AvatarCropMath.sourceRect(
            state = zoomed,
            imageWidth = 400f,
            imageHeight = 200f,
            cropDiameter = 100f
        )

        assertEquals(2f, zoomed.scale, 0.01f)
        assertEquals(150f, rect.left, 0.01f)
        assertEquals(50f, rect.top, 0.01f)
        assertEquals(100f, rect.size, 0.01f)
    }

    @Test
    fun scaleIsClampedBetweenCoverAndMax() {
        val tooSmall = AvatarCropMath.applyZoom(
            state = AvatarCropState(),
            zoom = 0.1f,
            centroidX = 0f,
            centroidY = 0f,
            cropCenterX = 0f,
            cropCenterY = 0f,
            imageWidth = 200f,
            imageHeight = 200f,
            cropDiameter = 100f
        )
        val tooLarge = AvatarCropMath.applyZoom(
            state = AvatarCropState(),
            zoom = 100f,
            centroidX = 0f,
            centroidY = 0f,
            cropCenterX = 0f,
            cropCenterY = 0f,
            imageWidth = 200f,
            imageHeight = 200f,
            cropDiameter = 100f
        )

        assertEquals(AvatarCropMath.MIN_USER_SCALE, tooSmall.scale)
        assertEquals(AvatarCropMath.MAX_USER_SCALE, tooLarge.scale)
    }

    @Test
    fun pixelRectStaysInsideTheBitmap() {
        val rect = AvatarCropMath.pixelRect(
            state = AvatarCropState(scale = AvatarCropMath.MAX_USER_SCALE, offsetX = -10_000f),
            imageWidth = 401,
            imageHeight = 199,
            cropDiameter = 100f
        )

        assertTrue(rect.x >= 0)
        assertTrue(rect.y >= 0)
        assertTrue(rect.size >= 1)
        assertTrue(rect.x + rect.size <= 401)
        assertTrue(rect.y + rect.size <= 199)
    }
}
