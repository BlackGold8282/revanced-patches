package app.revanced.extension.youtube.swipecontrols.controller.gesture.core

import android.content.Context
import android.util.TypedValue
import app.revanced.extension.youtube.patches.video.PlaybackSpeedPatch.userSelectedPlaybackSpeed
import app.revanced.extension.youtube.shared.VideoInformation
import app.revanced.extension.youtube.swipecontrols.controller.AudioVolumeController
import app.revanced.extension.youtube.swipecontrols.controller.ScreenBrightnessController
import app.revanced.extension.youtube.swipecontrols.misc.ScrollDistanceHelper
import app.revanced.extension.youtube.swipecontrols.misc.SwipeControlsOverlay
import app.revanced.extension.youtube.swipecontrols.misc.applyDimension
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * describes a class that controls volume and brightness based on scrolling events
 */
interface VolumeAndBrightnessScroller {
    /**
     * submit a scroll for volume adjustment
     *
     * @param distance the scroll distance
     */
    fun scrollVolume(distance: Double)

    /**
     * submit a scroll for brightness adjustment
     *
     * @param distance the scroll distance
     */
    fun scrollBrightness(distance: Double)

    /**
     * submit a scroll for speed adjustment
     *
     * @param distance the scroll distance
     */
    fun scrollSpeed(distance: Double)

    /**
     * submit a scroll for seek adjustment
     *
     * @param distance the scroll distance
     */
    fun scrollSeek(distance: Double)

    /**
     * reset all scroll distances to zero
     */
    fun resetScroller()
}

/**
 * handles scrolling of volume and brightness, adjusts them using the provided controllers and updates the overlay
 *
 * @param context context to create the scrollers in
 * @param volumeController volume controller instance. if null, volume control is disabled
 * @param screenController screen brightness controller instance. if null, brightness control is disabled
 * @param overlayController overlay controller instance
 * @param volumeDistance unit distance for volume scrolling, in dp
 * @param brightnessDistance unit distance for brightness scrolling, in dp
 */
class VolumeAndBrightnessScrollerImpl(
    context: Context,
    private val volumeController: AudioVolumeController?,
    private val screenController: ScreenBrightnessController?,
    private val overlayController: SwipeControlsOverlay,
    volumeDistance: Float = 10.0f,
    brightnessDistance: Float = 1.0f,
    speedDistance: Float = 10.0f,
    seekDistance: Float = 10.0f,
) : VolumeAndBrightnessScroller {

    // region volume
    private val volumeScroller =
        ScrollDistanceHelper(
            volumeDistance.applyDimension(
                context,
                TypedValue.COMPLEX_UNIT_DIP,
            ),
        ) { _, _, direction ->
            volumeController?.run {
                volume += direction
                overlayController.onVolumeChanged(volume, maxVolume)
            }
        }

    override fun scrollVolume(distance: Double) = volumeScroller.add(distance)
    // endregion

    // region brightness
    private val brightnessScroller =
        ScrollDistanceHelper(
            brightnessDistance.applyDimension(
                context,
                TypedValue.COMPLEX_UNIT_DIP,
            ),
        ) { _, _, direction ->
            screenController?.run {
                val shouldAdjustBrightness =
                    if (host.config.shouldLowestValueEnableAutoBrightness) {
                        screenBrightness > 0 || direction > 0
                    } else {
                        screenBrightness >= 0 || direction >= 0
                    }

                if (shouldAdjustBrightness) {
                    screenBrightness += direction
                } else {
                    restoreDefaultBrightness()
                }
                overlayController.onBrightnessChanged(screenBrightness)
            }
        }

    override fun scrollBrightness(distance: Double) = brightnessScroller.add(distance)
    // endregion

    // region speed
    private val speedScroller = ScrollDistanceHelper(
        speedDistance.applyDimension(
            context,
            TypedValue.COMPLEX_UNIT_DIP,
        ),
    ) { _, _, direction ->
        val currentSpeed = VideoInformation.getPlaybackSpeed().toFloat()

        // Convert current speed to an integer representation (multiply by 100)
        // in order to fix a floating-point imprecision (1.04999 instead of 1.05)
        val currentSpeedInt = (currentSpeed * 100).roundToInt()

        // Calculate the new speed as an integer, incrementing by 5 (which represents 0.05)
        val newSpeedInt = (currentSpeedInt - direction * 5).coerceIn(5, 800)

        // Convert back to a float
        val newSpeed = newSpeedInt / 100f

        VideoInformation.overridePlaybackSpeed(newSpeed)
        userSelectedPlaybackSpeed(newSpeed)
        overlayController.onSpeedChanged(newSpeed)
    }

    override fun scrollSpeed(distance: Double) = speedScroller.add(distance)
    // endregion

    // region seek
    private val seekScroller = ScrollDistanceHelper(
        seekDistance.applyDimension(
            context,
            TypedValue.COMPLEX_UNIT_DIP,
        ),
    ) { _, _, direction ->
        val seekAmountMillis = (direction * -500.0).roundToLong()

        VideoInformation.seekToRelative(seekAmountMillis)
        overlayController.onSeekChanged(seekAmountMillis.toInt())
    }

    override fun scrollSeek(distance: Double) = seekScroller.add(distance)
    // endregion

    override fun resetScroller() {
        volumeScroller.reset()
        brightnessScroller.reset()
        speedScroller.reset()
        seekScroller.reset()
    }
}
