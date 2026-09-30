package expo.modules.mediacontrol

import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * Expo Media Control Module for Android
 *
 * Bridges JavaScript to a Media3 session. The app's own player reports its state through this
 * module; [MediaControlPlayer] mirrors it for the system (notification, lock screen, Bluetooth,
 * Wear OS, cars) and turns system commands back into `mediaControlEvent` events.
 *
 * All functions run on the main thread, which Media3 requires for the player and the session.
 */
class ExpoMediaControlModule : Module() {

  /** Keeps the [MediaPlaybackService] bound (and therefore its session alive) while enabled */
  private var controllerFuture: ListenableFuture<MediaController>? = null

  private var isControlsEnabled = false

  /** Metadata as sent from JavaScript, returned by getCurrentMetadata() */
  private var currentMetadata: Map<String, Any?>? = null

  override fun definition() = ModuleDefinition {
    Name("ExpoMediaControl")

    Events("mediaControlEvent")

    OnDestroy {
      // OnDestroy may run off the main thread; the player must only be touched on it
      if (Looper.myLooper() == Looper.getMainLooper()) {
        disableMediaControls()
      } else {
        Handler(Looper.getMainLooper()).post { disableMediaControls() }
      }
    }

    /**
     * Enable media controls, or apply a new configuration when already enabled
     */
    AsyncFunction("enableMediaControls") { options: Map<String, Any?>? ->
      enableMediaControls(options ?: emptyMap())
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("disableMediaControls") { ->
      disableMediaControls()
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("updateMetadata") { metadata: Map<String, Any?> ->
      currentMetadata = metadata
      MediaControlCenter.player.setMetadata(metadata)
    }.runOnQueue(Queues.MAIN)

    /**
     * @param state - The playback state
     * @param position - The current position in seconds (optional)
     * @param playbackRate - The playback rate/speed (optional)
     */
    AsyncFunction("updatePlaybackState") { state: Int, position: Double?, playbackRate: Double? ->
      MediaControlCenter.player.setPlaybackState(state, position, playbackRate)
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("resetControls") { ->
      currentMetadata = null
      MediaControlCenter.player.reset()
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("isEnabled") { ->
      isControlsEnabled
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("getCurrentMetadata") { ->
      currentMetadata
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("getCurrentState") { ->
      MediaControlCenter.player.playbackStateValue
    }.runOnQueue(Queues.MAIN)
  }

  private fun enableMediaControls(options: Map<String, Any?>) {
    val context = appContext.reactContext ?: throw ReactContextLostException()

    @Suppress("UNCHECKED_CAST")
    val capabilities = options["capabilities"] as? List<String>
    @Suppress("UNCHECKED_CAST")
    val compactCapabilities = options["compactCapabilities"] as? List<String>
    val skipInterval = ((options["android"] as? Map<*, *>)?.get("skipInterval") as? Number)?.toDouble()

    val player = MediaControlCenter.player
    player.commandListener = MediaControlCommandListener { command, data ->
      sendEvent(
        "mediaControlEvent",
        mapOf(
          "command" to command,
          "data" to data,
          "timestamp" to System.currentTimeMillis()
        )
      )
    }
    player.setConfiguration(capabilities, compactCapabilities, skipInterval)
    MediaControlCenter.refreshMediaButtonPreferences()

    if (controllerFuture == null) {
      // Connecting a controller binds (and creates) the service; Media3 then moves it to the
      // foreground and posts the notification while playing
      val token = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
      controllerFuture = MediaController.Builder(context, token).buildAsync()
    }
    isControlsEnabled = true
  }

  private fun disableMediaControls() {
    val player = MediaControlCenter.player
    player.reset()
    player.commandListener = null
    currentMetadata = null

    controllerFuture?.let { MediaController.releaseFuture(it) }
    controllerFuture = null
    appContext.reactContext?.let { context ->
      context.stopService(Intent(context, MediaPlaybackService::class.java))
    }
    isControlsEnabled = false
  }
}

private class ReactContextLostException :
  CodedException("ENABLE_FAILED", "React context is not available, cannot enable media controls", null)
