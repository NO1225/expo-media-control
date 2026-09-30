package expo.modules.mediacontrol

import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PercentageRating
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.StarRating
import androidx.media3.common.ThumbRating
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A Media3 [Player] that doesn't play anything itself.
 *
 * The app plays audio with its own player (expo-audio, react-native-video, ...) and reports the
 * state from JavaScript. This class mirrors that state for the [androidx.media3.session.MediaSession]
 * (notification, lock screen, Bluetooth, Wear OS, cars) and turns the commands coming from those
 * surfaces into [MediaControlCommandListener] events for JavaScript.
 *
 * All methods must be called on the main thread.
 */
@OptIn(UnstableApi::class)
class MediaControlPlayer : SimpleBasePlayer(Looper.getMainLooper()) {

  var commandListener: MediaControlCommandListener? = null

  /** Enabled commands (null = all enabled, for backward compatibility) */
  var capabilities: List<String>? = null
    private set

  /** Commands requested for the compact notification / system player slots */
  var compactCapabilities: List<String>? = null
    private set

  var skipIntervalSeconds: Double = DEFAULT_SKIP_INTERVAL_SECONDS
    private set

  /** Current playback state using the JavaScript PlaybackState values */
  var playbackStateValue: Int = STATE_VALUE_NONE
    private set

  private var metadata: MediaMetadata? = null
  private var mediaId: String = DEFAULT_MEDIA_ID
  private var durationMs: Long = C.TIME_UNSET
  private var isLiveStream = false

  // Position is stored as a (position, timestamp) pair so it can be extrapolated while playing
  private var positionMs: Long = 0
  private var positionUpdateTimeMs: Long = SystemClock.elapsedRealtime()
  private var playbackSpeed: Float = 1f

  // Created once per error so listeners aren't notified of a "new" error on every state refresh
  private var playerError: PlaybackException? = null

  // True between a play command from a controller and JavaScript confirming it
  private var playAwaitingConfirmation = false

  // =============================================
  // STATE UPDATES FROM JAVASCRIPT
  // =============================================

  fun setConfiguration(caps: List<String>?, compactCaps: List<String>?, skipInterval: Double?) {
    capabilities = caps
    compactCapabilities = compactCaps
    if (skipInterval != null && skipInterval > 0) {
      skipIntervalSeconds = skipInterval
    }
    invalidateState()
  }

  fun setMetadata(values: Map<String, Any?>) {
    val builder = MediaMetadata.Builder()
      .setIsPlayable(true)
      .setIsBrowsable(false)
      .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)

    (values["title"] as? String)?.let { builder.setTitle(it) }
    (values["artist"] as? String)?.let { builder.setArtist(it) }
    (values["album"] as? String)?.let { builder.setAlbumTitle(it) }
    (values["genre"] as? String)?.let { builder.setGenre(it) }
    (values["trackNumber"] as? Number)?.let { builder.setTrackNumber(it.toInt()) }
    (values["albumTrackCount"] as? Number)?.let { builder.setTotalTrackCount(it.toInt()) }
    (values["date"] as? String)?.let { date ->
      date.take(4).toIntOrNull()?.let { builder.setReleaseYear(it) }
    }

    val duration = (values["duration"] as? Number)?.toDouble()
    durationMs = if (duration != null && duration > 0) (duration * 1000).toLong() else C.TIME_UNSET
    if (durationMs != C.TIME_UNSET) {
      builder.setDurationMs(durationMs)
    }

    // Media3 loads (and downsamples/caches) the artwork itself. If it can't be loaded, the
    // notification is still updated, just without artwork.
    val artworkUri = ((values["artwork"] as? Map<*, *>)?.get("uri") as? String)?.takeIf { it.isNotEmpty() }
    artworkUri?.let { builder.setArtworkUri(Uri.parse(it)) }

    (values["rating"] as? Map<*, *>)?.let { toRating(it) }?.let { builder.setUserRating(it) }

    isLiveStream = values["isLiveStream"] as? Boolean ?: false

    metadata = builder.build()
    // A new id tells controllers the track changed; the same values keep the same id
    mediaId = listOf(values["title"], values["artist"], values["album"], artworkUri).joinToString("|")

    (values["elapsedTime"] as? Number)?.let { setPosition((it.toDouble() * 1000).toLong()) }

    invalidateState()
  }

  fun setPlaybackState(state: Int, positionSeconds: Double?, rate: Double?) {
    if (positionSeconds != null) {
      setPosition((positionSeconds * 1000).toLong())
    } else {
      // Freeze the extrapolated position at the moment the state changes
      setPosition(currentPositionEstimateMs())
    }
    // Media3 needs a positive speed; a rate of 0 (paused) just means "not advancing"
    if (rate != null && rate > 0) {
      playbackSpeed = rate.toFloat()
    }
    // Android sends media keys to the session that became active last. The app's real player can
    // own a session too (expo-audio creates one per player), and after a remote play it starts
    // later than our optimistic update. Stepping through paused makes the confirmed play the
    // latest activation, so next/previous keep reaching this session.
    if (state == STATE_VALUE_PLAYING && playbackStateValue == STATE_VALUE_PLAYING && playAwaitingConfirmation) {
      playbackStateValue = STATE_VALUE_PAUSED
      invalidateState()
    }
    playAwaitingConfirmation = false
    playbackStateValue = state
    playerError = if (state == STATE_VALUE_ERROR) {
      playerError ?: PlaybackException("Playback error", null, PlaybackException.ERROR_CODE_UNSPECIFIED)
    } else {
      null
    }
    invalidateState()
  }

  fun reset() {
    metadata = null
    mediaId = DEFAULT_MEDIA_ID
    durationMs = C.TIME_UNSET
    isLiveStream = false
    playbackStateValue = STATE_VALUE_NONE
    playAwaitingConfirmation = false
    playerError = null
    playbackSpeed = 1f
    setPosition(0)
    invalidateState()
  }

  fun isCapabilityEnabled(command: String): Boolean {
    val caps = capabilities ?: return true
    return caps.contains(command)
  }

  // =============================================
  // SIMPLE BASE PLAYER
  // =============================================

  override fun getState(): State {
    val builder = State.Builder()
      .setAvailableCommands(buildAvailableCommands())
      .setPlaybackParameters(PlaybackParameters(playbackSpeed))
      .setSeekBackIncrementMs(skipIntervalMs())
      .setSeekForwardIncrementMs(skipIntervalMs())

    // NONE means there is nothing to show: an empty playlist hides the notification
    if (playbackStateValue == STATE_VALUE_NONE) {
      return builder
        .setPlaylist(emptyList())
        .setPlaybackState(Player.STATE_IDLE)
        .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .build()
    }

    val mediaMetadata = metadata ?: MediaMetadata.EMPTY
    val itemBuilder = MediaItemData.Builder(mediaId)
      .setMediaItem(MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(mediaMetadata).build())
      .setMediaMetadata(mediaMetadata)
      .setDurationUs(if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1000)
      .setIsSeekable(!isLiveStream && isCapabilityEnabled("seek"))
      .setIsDynamic(isLiveStream)
    if (isLiveStream) {
      itemBuilder.setLiveConfiguration(MediaItem.LiveConfiguration.UNSET)
    }

    val basePositionMs = positionMs
    val baseTimeMs = positionUpdateTimeMs
    val speed = playbackSpeed
    val advancing = playbackStateValue == STATE_VALUE_PLAYING
    val maxPositionMs = durationMs
    builder
      .setPlaylist(listOf(itemBuilder.build()))
      .setCurrentMediaItemIndex(0)
      .setContentPositionMs(
        PositionSupplier {
          val position = if (advancing) {
            basePositionMs + ((SystemClock.elapsedRealtime() - baseTimeMs) * speed).toLong()
          } else {
            basePositionMs
          }
          if (maxPositionMs != C.TIME_UNSET) position.coerceIn(0, maxPositionMs) else position.coerceAtLeast(0)
        }
      )

    when (playbackStateValue) {
      STATE_VALUE_PLAYING -> builder
        .setPlaybackState(Player.STATE_READY)
        .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
      STATE_VALUE_PAUSED -> builder
        .setPlaybackState(Player.STATE_READY)
        .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
      STATE_VALUE_BUFFERING -> builder
        .setPlaybackState(Player.STATE_BUFFERING)
        .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
      STATE_VALUE_ERROR -> builder
        .setPlaybackState(Player.STATE_IDLE)
        .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setPlayerError(playerError)
      else -> builder // STOPPED
        .setPlaybackState(Player.STATE_IDLE)
        .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    }
    return builder.build()
  }

  override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    // Update optimistically so the UI responds immediately; JavaScript confirms with
    // updatePlaybackState() once its player actually changed state
    setPosition(currentPositionEstimateMs())
    playbackStateValue = if (playWhenReady) STATE_VALUE_PLAYING else STATE_VALUE_PAUSED
    playAwaitingConfirmation = playWhenReady
    dispatch(if (playWhenReady) "play" else "pause")
    return Futures.immediateVoidFuture()
  }

  override fun handleStop(): ListenableFuture<*> {
    setPosition(currentPositionEstimateMs())
    playbackStateValue = STATE_VALUE_STOPPED
    dispatch("stop")
    return Futures.immediateVoidFuture()
  }

  override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
    when (seekCommand) {
      Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> dispatch("nextTrack")
      Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> dispatch("previousTrack")
      Player.COMMAND_SEEK_FORWARD -> {
        if (positionMs != C.TIME_UNSET) setPosition(positionMs)
        dispatch("skipForward", mapOf("interval" to skipIntervalSeconds))
      }
      Player.COMMAND_SEEK_BACK -> {
        if (positionMs != C.TIME_UNSET) setPosition(positionMs)
        dispatch("skipBackward", mapOf("interval" to skipIntervalSeconds))
      }
      Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> {
        if (positionMs != C.TIME_UNSET) {
          setPosition(positionMs)
          dispatch("seek", mapOf("position" to positionMs / 1000.0))
        }
      }
    }
    return Futures.immediateVoidFuture()
  }

  override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

  /** Called by the session when a controller sets a rating */
  fun onRatingRequested(rating: Rating) {
    val data: Map<String, Any?> = when (rating) {
      is HeartRating -> mapOf("type" to "heart", "rating" to rating.isHeart)
      is ThumbRating -> mapOf("type" to "thumbsUpDown", "rating" to rating.isThumbsUp)
      is StarRating -> mapOf(
        "type" to when (rating.maxStars) {
          3 -> "threeStars"
          4 -> "fourStars"
          else -> "fiveStars"
        },
        "rating" to rating.starRating.toDouble()
      )
      is PercentageRating -> mapOf("type" to "percentage", "rating" to rating.percent.toDouble())
      else -> return
    }
    dispatch("setRating", data)
  }

  // =============================================
  // HELPERS
  // =============================================

  private fun buildAvailableCommands(): Player.Commands {
    val builder = Player.Commands.Builder().addAll(
      Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
      Player.COMMAND_GET_TIMELINE,
      Player.COMMAND_GET_METADATA,
      Player.COMMAND_RELEASE
    )
    if (isCapabilityEnabled("play") || isCapabilityEnabled("pause")) {
      builder.add(Player.COMMAND_PLAY_PAUSE)
    }
    if (isCapabilityEnabled("stop")) {
      builder.add(Player.COMMAND_STOP)
    }
    if (isCapabilityEnabled("nextTrack")) {
      builder.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    }
    if (isCapabilityEnabled("previousTrack")) {
      builder.addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    }
    if (isCapabilityEnabled("skipForward")) {
      builder.add(Player.COMMAND_SEEK_FORWARD)
    }
    if (isCapabilityEnabled("skipBackward")) {
      builder.add(Player.COMMAND_SEEK_BACK)
    }
    if (isCapabilityEnabled("seek") && !isLiveStream) {
      builder.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    }
    return builder.build()
  }

  private fun toRating(value: Map<*, *>): Rating? {
    val ratingValue = value["value"]
    return when (value["type"]) {
      "heart" -> (ratingValue as? Boolean)?.let { HeartRating(it) }
      "thumbsUpDown" -> (ratingValue as? Boolean)?.let { ThumbRating(it) }
      "threeStars", "fourStars", "fiveStars" -> {
        val maxStars = when (value["type"]) {
          "threeStars" -> 3
          "fourStars" -> 4
          else -> 5
        }
        (ratingValue as? Number)?.toFloat()
          ?.takeIf { it in 0f..maxStars.toFloat() }
          ?.let { StarRating(maxStars, it) }
      }
      "percentage" -> (ratingValue as? Number)?.toFloat()
        ?.takeIf { it in 0f..100f }
        ?.let { PercentageRating(it) }
      else -> null
    }
  }

  private fun skipIntervalMs(): Long = (skipIntervalSeconds * 1000).toLong().coerceAtLeast(1)

  private fun setPosition(newPositionMs: Long) {
    positionMs = newPositionMs.coerceAtLeast(0)
    positionUpdateTimeMs = SystemClock.elapsedRealtime()
  }

  private fun currentPositionEstimateMs(): Long {
    if (playbackStateValue != STATE_VALUE_PLAYING) return positionMs
    return positionMs + ((SystemClock.elapsedRealtime() - positionUpdateTimeMs) * playbackSpeed).toLong()
  }

  private fun dispatch(command: String, data: Map<String, Any?>? = null) {
    commandListener?.onCommand(command, data)
  }

  companion object {
    const val DEFAULT_SKIP_INTERVAL_SECONDS = 15.0
    private const val DEFAULT_MEDIA_ID = "expo-media-control"

    // PlaybackState values (matching the TypeScript enum)
    const val STATE_VALUE_NONE = 0
    const val STATE_VALUE_STOPPED = 1
    const val STATE_VALUE_PLAYING = 2
    const val STATE_VALUE_PAUSED = 3
    const val STATE_VALUE_BUFFERING = 4
    const val STATE_VALUE_ERROR = 5
  }
}

fun interface MediaControlCommandListener {
  fun onCommand(command: String, data: Map<String, Any?>?)
}
