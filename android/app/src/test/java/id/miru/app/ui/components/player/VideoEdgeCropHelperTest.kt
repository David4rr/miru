package id.miru.app.ui.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEdgeCropHelperTest {

    @Test
    fun testStandard480pMacroblockPaddingCrop() {
        // 854x480 has 10px macroblock padding on the right (854 % 16 = 6 -> pad = 10px = 1.17%)
        val (scaleX, scaleY) = VideoEdgeCropHelper.calculateScalesFromDimensions(854, 480)

        // scaleX must exceed 1.031f to eliminate the 10px padding plus safety margin
        assertTrue("scaleX ($scaleX) must be >= 1.030f to eliminate 10px right padding", scaleX >= 1.030f)
        // 480 is cleanly divisible by 16, so scaleY should be at baseline safe scale
        assertEquals(VideoEdgeCropHelper.BASE_SAFE_SCALE, scaleY, 0.001f)
    }

    @Test
    fun testStandard1080pMacroblockPaddingCrop() {
        // 1920x1080: 1920 is divisible by 16; 1080 % 16 = 8 -> 8px padding at bottom (0.74%)
        val (scaleX, scaleY) = VideoEdgeCropHelper.calculateScalesFromDimensions(1920, 1080)

        assertEquals(VideoEdgeCropHelper.BASE_SAFE_SCALE, scaleX, 0.001f)
        assertTrue("scaleY ($scaleY) must be >= 1.022f to eliminate 8px bottom padding", scaleY >= 1.022f)
    }

    @Test
    fun test720pWebRipPaddingCrop() {
        // 1364x768: 1364 % 16 = 4 -> 12px padding on the right
        val (scaleX, scaleY) = VideoEdgeCropHelper.calculateScalesFromDimensions(1364, 768)

        assertTrue("scaleX ($scaleX) must be >= 1.025f to eliminate 12px right padding", scaleX >= 1.025f)
        assertEquals(VideoEdgeCropHelper.BASE_SAFE_SCALE, scaleY, 0.001f)
    }

    @Test
    fun testTheatrical21x9PaddingCrop() {
        // 1920x804: 804 % 16 = 4 -> 12px padding at bottom
        val (scaleX, scaleY) = VideoEdgeCropHelper.calculateScalesFromDimensions(1920, 804)

        assertEquals(VideoEdgeCropHelper.BASE_SAFE_SCALE, scaleX, 0.001f)
        assertTrue("scaleY ($scaleY) must be >= 1.035f to eliminate bottom padding", scaleY >= 1.035f)
    }

    @Test
    fun testRotatedVideoSwapDimensions() {
        // Video 480x854 with 90 degree rotation is raw 854x480
        val (scaleX, scaleY) = VideoEdgeCropHelper.calculateScalesFromDimensions(480, 854, unappliedRotationDegrees = 90)

        assertTrue("Rotated video scaleX ($scaleX) must handle raw 854 width", scaleX >= 1.030f)
        assertEquals(VideoEdgeCropHelper.BASE_SAFE_SCALE, scaleY, 0.001f)
    }

    @Test
    fun testZeroOrUnknownDimensionsFallback() {
        val (scaleX, scaleY) = VideoEdgeCropHelper.calculateScalesFromDimensions(0, 0)

        assertEquals(1.012f, scaleX, 0.001f)
        assertEquals(1.012f, scaleY, 0.001f)
    }
}
