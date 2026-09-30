package expo.modules.mediacontrol

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Process-wide state shared between the Expo module and the [MediaPlaybackService].
 * Must only be accessed on the main thread.
 */
@OptIn(UnstableApi::class)
object MediaControlCenter {
  val player: MediaControlPlayer by lazy { MediaControlPlayer() }

  /** The session of the running [MediaPlaybackService], if any */
  var session: MediaSession? = null

  fun refreshMediaButtonPreferences() {
    session?.setMediaButtonPreferences(buildMediaButtonPreferences())
  }

  /**
   * Builds the buttons shown next to play/pause (notification, lock screen, Android 13+ system
   * player, Wear OS, ...).
   *
   * The back/forward slots are chosen from `compactCapabilities` (the commands before/after
   * play-pause). Without it, previous/next are preferred and skip backward/forward are used when
   * track navigation is disabled. Previous/next in their own slot are left to Media3's default
   * handling so they stay regular "skip to previous/next" actions for cars and watches.
   */
  fun buildMediaButtonPreferences(): List<CommandButton> {
    val player = player
    val capabilities = player.capabilities
    val slotCandidates = setOf("previousTrack", "nextTrack", "skipBackward", "skipForward", "stop")

    val backCommand: String?
    val forwardCommand: String?
    val compact = player.compactCapabilities
      ?.filter { it == "play" || it == "pause" || (it in slotCandidates && player.isCapabilityEnabled(it)) }
    if (!compact.isNullOrEmpty()) {
      val playIndex = compact.indexOfFirst { it == "play" || it == "pause" }
      if (playIndex >= 0) {
        backCommand = compact.subList(0, playIndex).lastOrNull()
        forwardCommand = compact.subList(playIndex + 1, compact.size)
          .firstOrNull { it != "play" && it != "pause" }
      } else {
        backCommand = compact.getOrNull(0)
        forwardCommand = compact.getOrNull(1)
      }
    } else {
      backCommand = listOf("previousTrack", "skipBackward").firstOrNull { player.isCapabilityEnabled(it) }
      forwardCommand = listOf("nextTrack", "skipForward").firstOrNull { player.isCapabilityEnabled(it) }
    }

    val buttons = mutableListOf<CommandButton>()
    if (backCommand != null && backCommand != "previousTrack") {
      createButton(backCommand, CommandButton.SLOT_BACK)?.let { buttons.add(it) }
    }
    if (forwardCommand != null && forwardCommand != "nextTrack") {
      createButton(forwardCommand, CommandButton.SLOT_FORWARD)?.let { buttons.add(it) }
    }

    // Other explicitly enabled commands go to the overflow (expanded notification / extra slots).
    // Without explicit capabilities the default previous / play-pause / next layout is kept.
    if (capabilities != null) {
      val placed = setOf(backCommand, forwardCommand)
      for (command in capabilities.distinct()) {
        if (command in slotCandidates && command !in placed) {
          createButton(command, CommandButton.SLOT_OVERFLOW)?.let { buttons.add(it) }
        }
      }
    }
    return buttons
  }

  private fun createButton(command: String, slot: Int): CommandButton? {
    val interval = player.skipIntervalSeconds
    val icon: Int
    val playerCommand: Int
    val name: String
    when (command) {
      "previousTrack" -> {
        icon = CommandButton.ICON_PREVIOUS
        playerCommand = Player.COMMAND_SEEK_TO_PREVIOUS
        name = "Previous"
      }
      "nextTrack" -> {
        icon = CommandButton.ICON_NEXT
        playerCommand = Player.COMMAND_SEEK_TO_NEXT
        name = "Next"
      }
      "skipBackward" -> {
        icon = when (interval) {
          5.0 -> CommandButton.ICON_SKIP_BACK_5
          10.0 -> CommandButton.ICON_SKIP_BACK_10
          15.0 -> CommandButton.ICON_SKIP_BACK_15
          30.0 -> CommandButton.ICON_SKIP_BACK_30
          else -> CommandButton.ICON_SKIP_BACK
        }
        playerCommand = Player.COMMAND_SEEK_BACK
        name = "Skip Backward"
      }
      "skipForward" -> {
        icon = when (interval) {
          5.0 -> CommandButton.ICON_SKIP_FORWARD_5
          10.0 -> CommandButton.ICON_SKIP_FORWARD_10
          15.0 -> CommandButton.ICON_SKIP_FORWARD_15
          30.0 -> CommandButton.ICON_SKIP_FORWARD_30
          else -> CommandButton.ICON_SKIP_FORWARD
        }
        playerCommand = Player.COMMAND_SEEK_FORWARD
        name = "Skip Forward"
      }
      "stop" -> {
        icon = CommandButton.ICON_STOP
        playerCommand = Player.COMMAND_STOP
        name = "Stop"
      }
      else -> return null
    }
    return CommandButton.Builder(icon)
      .setPlayerCommand(playerCommand)
      .setDisplayName(name)
      .setSlots(slot)
      .build()
  }
}

/**
 * Media3 session service. Owns the [MediaSession] for [MediaControlCenter.player] and lets
 * Media3 manage the media notification and the foreground service state.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {

  companion object {
    private const val NOTIFICATION_ID = 1001
    private const val NOTIFICATION_CHANNEL_ID = "media_playback_channel"
    private const val SESSION_ID = "expo-media-control"
  }

  private var mediaSession: MediaSession? = null

  override fun onCreate() {
    super.onCreate()

    val sessionBuilder = MediaSession.Builder(this, MediaControlCenter.player)
      .setId(SESSION_ID)
      .setCallback(SessionCallback())
      .setMediaButtonPreferences(MediaControlCenter.buildMediaButtonPreferences())
    createLaunchIntent()?.let { sessionBuilder.setSessionActivity(it) }
    val session = sessionBuilder.build()
    mediaSession = session
    MediaControlCenter.session = session

    val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
      .setNotificationId(NOTIFICATION_ID)
      .setChannelId(NOTIFICATION_CHANNEL_ID)
      .setChannelName(R.string.expo_media_control_notification_channel_name)
      .build()
    findSmallIconResource()?.let { notificationProvider.setSmallIcon(it) }
    setMediaNotificationProvider(notificationProvider)
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onDestroy() {
    mediaSession?.let { session ->
      if (MediaControlCenter.session === session) {
        MediaControlCenter.session = null
      }
      // The player is shared and outlives the service, so only the session is released
      session.release()
    }
    mediaSession = null
    super.onDestroy()
  }

  private inner class SessionCallback : MediaSession.Callback {
    override fun onSetRating(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      rating: Rating
    ): ListenableFuture<SessionResult> {
      MediaControlCenter.player.onRatingRequested(rating)
      return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onSetRating(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaId: String,
      rating: Rating
    ): ListenableFuture<SessionResult> = onSetRating(session, controller, rating)

    override fun onMediaButtonEvent(
      session: MediaSession,
      controllerInfo: MediaSession.ControllerInfo,
      intent: Intent
    ): Boolean {
      // Many Bluetooth headsets and car kits only have next/previous keys. When track navigation
      // is disabled but skip forward/backward is enabled, use those keys to skip instead.
      val keyEvent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        ?: return false
      val player = MediaControlCenter.player
      val remapToSkip = when (keyEvent.keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT ->
          !player.isCapabilityEnabled("nextTrack") && player.isCapabilityEnabled("skipForward")
        KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
          !player.isCapabilityEnabled("previousTrack") && player.isCapabilityEnabled("skipBackward")
        else -> false
      }
      if (!remapToSkip) {
        return false
      }
      if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
        if (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) player.seekForward() else player.seekBack()
      }
      return true
    }
  }

  private fun createLaunchIntent(): PendingIntent? {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    } ?: return null
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
    return PendingIntent.getActivity(this, 0, launchIntent, flags)
  }

  /**
   * Small notification icon: the icon configured through the config plugin, then common icon
   * names, then the launcher icon. Returns null to keep Media3's default icon.
   */
  private fun findSmallIconResource(): Int? {
    return try {
      val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
      val iconName = appInfo.metaData?.getString("expo.modules.mediacontrol.NOTIFICATION_ICON")
      if (iconName != null) {
        val cleanIconName = iconName.substringAfterLast("/").substringBeforeLast(".")
        val resourceId = resources.getIdentifier(cleanIconName, "drawable", packageName)
        if (resourceId != 0) {
          return resourceId
        }
        println("⚠️ Custom notification icon '$cleanIconName' not found in drawable resources")
      }

      val standardIconNames = listOf(
        "ic_notification",
        "notification_icon",
        "ic_stat_notification",
        "ic_media_notification"
      )
      for (name in standardIconNames) {
        val resourceId = resources.getIdentifier(name, "drawable", packageName)
        if (resourceId != 0) {
          return resourceId
        }
      }

      resources.getIdentifier("ic_launcher", "mipmap", packageName).takeIf { it != 0 }
    } catch (e: Exception) {
      println("❌ Error getting notification icon: ${e.message}")
      null
    }
  }
}
