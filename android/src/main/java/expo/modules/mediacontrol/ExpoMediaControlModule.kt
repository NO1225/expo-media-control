package expo.modules.mediacontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Expo Media Control Module for Android
 * 
 * This module provides comprehensive media control functionality for Android applications,
 * including MediaSessionCompat integration, notification management, audio focus handling,
 * and background service support. It handles system media controls, lock screen integration,
 * and remote control events.
 */
class ExpoMediaControlModule : Module() {
  // =============================================
  // CONSTANTS AND CONFIGURATION
  // =============================================
  
  companion object {
    private const val NOTIFICATION_CHANNEL_ID = "media_control_channel"
    private const val NOTIFICATION_ID = 1001
    private const val TAG = "ExpoMediaControl"
    
    // PlaybackState constants (matching TypeScript enum)
    private const val PLAYBACK_STATE_NONE = 0
    private const val PLAYBACK_STATE_STOPPED = 1
    private const val PLAYBACK_STATE_PLAYING = 2
    private const val PLAYBACK_STATE_PAUSED = 3
    private const val PLAYBACK_STATE_BUFFERING = 4
    private const val PLAYBACK_STATE_ERROR = 5
  }

  // =============================================
  // PROPERTIES AND STATE MANAGEMENT
  // =============================================
  
  /// MediaSession for handling media controls and state
  private var mediaSession: MediaSessionCompat? = null
  
  /// Current media metadata
  private var currentMetadata: MutableMap<String, Any> = ConcurrentHashMap()
  
  /// Current playback state
  private var currentPlaybackState: Int = PLAYBACK_STATE_NONE
  
  /// Current playback position in milliseconds
  private var currentPosition: Long = 0L
  
  /// Whether media controls are currently enabled
  private var isControlsEnabled: Boolean = false
  
  /// Configuration options for the media controls
  private var controlOptions: MutableMap<String, Any> = ConcurrentHashMap()
  
  /// Audio manager for handling audio focus
  private val audioManager: AudioManager? by lazy {
    try {
      appContext.reactContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } catch (e: Exception) {
      println("❌ Failed to get AudioManager: ${e.message}")
      null
    }
  }
  
  /// Notification manager for showing media notifications
  private val notificationManager: NotificationManager? by lazy {
    try {
      appContext.reactContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    } catch (e: Exception) {
      println("❌ Failed to get NotificationManager: ${e.message}")
      null
    }
  }
  
  /// Audio focus request for Android O and above
  private var audioFocusRequest: AudioFocusRequest? = null
  
  /// Whether we currently have audio focus
  private var hasAudioFocus: Boolean = false

  // =============================================
  // MODULE DEFINITION
  // =============================================
  
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module
    Name("ExpoMediaControl")

    // =============================================
    // MAIN CONTROL METHODS
    // Methods for enabling/disabling and managing media controls
    // =============================================
    
    /**
     * Enable media controls with specified configuration
     * Sets up MediaSession, registers callbacks, creates notification channel, and requests audio focus
     */
    AsyncFunction("enableMediaControls") { options: Map<String, Any>?, promise: Promise ->
      try {
        enableMediaControls(options ?: emptyMap())
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("ENABLE_FAILED", "Failed to enable media controls: ${e.message}", e)
      }
    }

    /**
     * Disable media controls and clean up all resources
     * Stops MediaSession, removes notifications, releases audio focus, and cleans up state
     */
    AsyncFunction("disableMediaControls") { promise: Promise ->
      try {
        disableMediaControls()
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("DISABLE_FAILED", "Failed to disable media controls: ${e.message}", e)
      }
    }

    /**
     * Update the media metadata displayed in system controls
     * Updates MediaSession metadata and refreshes notification with new information
     */
    AsyncFunction("updateMetadata") { metadata: Map<String, Any>, promise: Promise ->
      try {
        updateMetadata(metadata)
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("UPDATE_METADATA_FAILED", "Failed to update metadata: ${e.message}", e)
      }
    }

    /**
     * Update the current playback state and position
     * Updates MediaSession playback state and refreshes notification accordingly
     */
    AsyncFunction("updatePlaybackState") { state: Int, position: Double?, promise: Promise ->
      try {
        updatePlaybackState(state, position)
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("UPDATE_STATE_FAILED", "Failed to update playback state: ${e.message}", e)
      }
    }

    /**
     * Reset all media control information to default state
     * Clears all metadata, resets playback state, and removes notifications
     */
    AsyncFunction("resetControls") { promise: Promise ->
      try {
        resetControls()
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("RESET_FAILED", "Failed to reset controls: ${e.message}", e)
      }
    }

    // =============================================
    // STATE QUERY METHODS
    // Methods for retrieving current state information
    // =============================================

    /**
     * Check if media controls are currently enabled
     * Returns whether MediaSession is active and ready to receive events
     */
    AsyncFunction("isEnabled") { ->
      isControlsEnabled
    }

    /**
     * Get the current media metadata
     * Returns the currently set metadata information as a map
     */
    AsyncFunction("getCurrentMetadata") { ->
      if (currentMetadata.isEmpty()) null else currentMetadata.toMap()
    }

    /**
     * Get the current playback state
     * Returns the current playback status as an integer
     */
    AsyncFunction("getCurrentState") { ->
      currentPlaybackState
    }

    // =============================================
    // EVENT DEFINITIONS
    // Define events that can be sent to JavaScript
    // =============================================
    
    /// Events that can be sent to JavaScript
    Events("mediaControlEvent", "audioInterruption", "volumeChange")
  }

  // =============================================
  // IMPLEMENTATION METHODS
  // Private methods that implement the actual functionality
  // =============================================

  /**
   * Enable media controls implementation
   * Sets up MediaSession, audio focus, and notification channel
   */
  private fun enableMediaControls(options: Map<String, Any>) {
    try {
      // Store configuration options
      controlOptions.clear()
      controlOptions.putAll(options)
      
      // Create notification channel for Android O and above
      createNotificationChannel()
      
      // Initialize MediaSession
      initializeMediaSession()
      
      // Request audio focus if configured
      if (shouldRequestAudioFocus()) {
        requestAudioFocus()
      }
      
      // Mark controls as enabled
      isControlsEnabled = true
      
      println("🤖 Media controls enabled successfully")
    } catch (e: Exception) {
      println("❌ Failed to enable media controls: ${e.message}")
      throw e
    }
  }

  /**
   * Disable media controls implementation
   * Cleans up MediaSession, releases audio focus, and removes notifications
   */
  private fun disableMediaControls() {
    try {
      // Release audio focus
      releaseAudioFocus()
      
      // Stop and release MediaSession
      mediaSession?.let { session ->
        session.isActive = false
        session.release()
        mediaSession = null
      }
      
      // Remove notification
      notificationManager?.cancel(NOTIFICATION_ID)
      
      // Reset state
      isControlsEnabled = false
      currentMetadata.clear()
      currentPlaybackState = PLAYBACK_STATE_NONE
      currentPosition = 0L
      controlOptions.clear()
      
      println("🤖 Media controls disabled successfully")
    } catch (e: Exception) {
      println("❌ Failed to disable media controls: ${e.message}")
      throw e
    }
  }

  /**
   * Update metadata implementation
   * Converts metadata map and updates MediaSession and notification
   */
  private fun updateMetadata(metadata: Map<String, Any>) {
    try {
      // Store current metadata
      currentMetadata.clear()
      currentMetadata.putAll(metadata)
      
      // Build MediaMetadata
      val metadataBuilder = MediaMetadataCompat.Builder()
      
      // Basic information
      metadata["title"]?.let { metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.toString()) }
      metadata["artist"]?.let { metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it.toString()) }
      metadata["album"]?.let { metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it.toString()) }
      metadata["genre"]?.let { metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_GENRE, it.toString()) }
      
      // Duration and track information
      (metadata["duration"] as? Number)?.let { 
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (it.toDouble() * 1000).toLong())
      }
      (metadata["trackNumber"] as? Number)?.let { 
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, it.toLong())
      }
      (metadata["albumTrackCount"] as? Number)?.let { 
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, it.toLong())
      }
      
      // Handle artwork
      val artworkMap = metadata["artwork"] as? Map<String, Any>
      val artworkUri = artworkMap?.get("uri") as? String
      
      if (artworkUri != null) {
        // Load artwork asynchronously
        GlobalScope.launch(Dispatchers.IO) {
          try {
            println("🤖 Loading artwork from: $artworkUri")
            val bitmap = loadArtwork(artworkUri)
            if (bitmap != null) {
              println("🤖 Artwork loaded successfully: ${bitmap.width}x${bitmap.height}")
              metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
              metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            } else {
              println("❌ Failed to load artwork: bitmap is null")
            }
            
            // Update MediaSession on main thread
            withContext(Dispatchers.Main) {
              mediaSession?.setMetadata(metadataBuilder.build())
              updateNotification()
              println("🤖 MediaSession updated with artwork")
            }
          } catch (e: Exception) {
            println("❌ Failed to load artwork: ${e.message}")
            e.printStackTrace()
            // Update without artwork
            withContext(Dispatchers.Main) {
              mediaSession?.setMetadata(metadataBuilder.build())
              updateNotification()
            }
          }
        }
      } else {
        // Update without artwork
        mediaSession?.setMetadata(metadataBuilder.build())
        updateNotification()
      }
      
      println("🤖 Metadata updated: ${metadata["title"]} - ${metadata["artist"]}")
    } catch (e: Exception) {
      println("❌ Failed to update metadata: ${e.message}")
      e.printStackTrace()
      throw e
    }
  }

  /**
   * Update playback state implementation
   * Updates MediaSession playback state and refreshes notification
   */
  private fun updatePlaybackState(state: Int, position: Double?) {
    try {
      currentPlaybackState = state
      
      // Update position if provided
      position?.let { 
        currentPosition = (it * 1000).toLong() // Convert to milliseconds
      }
      
      // Convert to MediaSession playback state
      val playbackState = when (state) {
        PLAYBACK_STATE_NONE -> PlaybackStateCompat.STATE_NONE
        PLAYBACK_STATE_STOPPED -> PlaybackStateCompat.STATE_STOPPED
        PLAYBACK_STATE_PLAYING -> PlaybackStateCompat.STATE_PLAYING
        PLAYBACK_STATE_PAUSED -> PlaybackStateCompat.STATE_PAUSED
        PLAYBACK_STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
        PLAYBACK_STATE_ERROR -> PlaybackStateCompat.STATE_ERROR
        else -> PlaybackStateCompat.STATE_NONE
      }
      
      // Build PlaybackState
      val stateBuilder = PlaybackStateCompat.Builder()
        .setState(playbackState, currentPosition, if (playbackState == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0.0f)
        .setActions(getPlaybackActions())
      
      // Update MediaSession
      mediaSession?.setPlaybackState(stateBuilder.build())
      
      // Update notification
      updateNotification()
      
      println("🤖 Playback state updated: $state, position: ${currentPosition}ms")
    } catch (e: Exception) {
      println("❌ Failed to update playback state: ${e.message}")
      throw e
    }
  }

  /**
   * Reset controls implementation
   * Clears all information and returns to initial state
   */
  private fun resetControls() {
    try {
      currentMetadata.clear()
      currentPlaybackState = PLAYBACK_STATE_NONE
      currentPosition = 0L
      
      // Reset MediaSession
      mediaSession?.setMetadata(null)
      mediaSession?.setPlaybackState(
        PlaybackStateCompat.Builder()
          .setState(PlaybackStateCompat.STATE_NONE, 0L, 0.0f)
          .build()
      )
      
      // Remove notification
      notificationManager?.cancel(NOTIFICATION_ID)
      
      println("🤖 Controls reset to initial state")
    } catch (e: Exception) {
      println("❌ Failed to reset controls: ${e.message}")
      throw e
    }
  }

  // =============================================
  // MEDIA SESSION MANAGEMENT
  // Methods for creating and managing MediaSession
  // =============================================

  /**
   * Initialize MediaSession with proper configuration
   * Sets up callbacks and prepares for media control handling
   */
  private fun initializeMediaSession() {
    try {
      val context = appContext.reactContext
      if (context == null) {
        println("❌ React context is null, cannot initialize MediaSession")
        return
      }
      
      // Create MediaSession
      mediaSession = MediaSessionCompat(context, TAG).apply {
        // Set callback for handling media button events
        setCallback(object : MediaSessionCompat.Callback() {
          override fun onPlay() {
            handleMediaCommand("play", null)
          }

          override fun onPause() {
            handleMediaCommand("pause", null)
          }

          override fun onStop() {
            handleMediaCommand("stop", null)
          }

          override fun onSkipToNext() {
            handleMediaCommand("nextTrack", null)
          }

          override fun onSkipToPrevious() {
            handleMediaCommand("previousTrack", null)
          }

          override fun onSeekTo(pos: Long) {
            val data = mapOf("position" to (pos / 1000.0)) // Convert to seconds
            handleMediaCommand("seek", data)
          }

          override fun onFastForward() {
            val skipInterval = getSkipInterval()
            val data = mapOf("interval" to skipInterval)
            handleMediaCommand("skipForward", data)
          }

          override fun onRewind() {
            val skipInterval = getSkipInterval()
            val data = mapOf("interval" to skipInterval)
            handleMediaCommand("skipBackward", data)
          }

          override fun onSetRating(rating: RatingCompat) {
            val data = mapOf(
              "rating" to rating.getRating(),
              "type" to when (rating.ratingStyle) {
                RatingCompat.RATING_HEART -> "heart"
                RatingCompat.RATING_THUMB_UP_DOWN -> "thumbsUpDown"
                RatingCompat.RATING_3_STARS -> "threeStars"
                RatingCompat.RATING_4_STARS -> "fourStars"
                RatingCompat.RATING_5_STARS -> "fiveStars"
                RatingCompat.RATING_PERCENTAGE -> "percentage"
                else -> "unknown"
              }
            )
            handleMediaCommand("setRating", data)
          }
        })
        
        // Set session flags
        setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        
        // Activate the session
        isActive = true
      }
      
      println("🤖 MediaSession initialized successfully")
    } catch (e: Exception) {
      println("❌ Failed to initialize MediaSession: ${e.message}")
      e.printStackTrace()
      throw e
    }
  }

  /**
   * Get supported playback actions based on configuration
   * Returns the actions that should be available in the media controls
   */
  private fun getPlaybackActions(): Long {
    var actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                 PlaybackStateCompat.ACTION_STOP or
                 PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                 PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                 PlaybackStateCompat.ACTION_SEEK_TO
    
    // Add skip actions if configured
    val capabilities = (controlOptions["capabilities"] as? List<*>)?.map { it.toString() }
    if (capabilities?.contains("skipForward") == true) {
      actions = actions or PlaybackStateCompat.ACTION_FAST_FORWARD
    }
    if (capabilities?.contains("skipBackward") == true) {
      actions = actions or PlaybackStateCompat.ACTION_REWIND
    }
    if (capabilities?.contains("setRating") == true) {
      actions = actions or PlaybackStateCompat.ACTION_SET_RATING
    }
    
    return actions
  }

  // =============================================
  // NOTIFICATION MANAGEMENT
  // Methods for creating and updating media notifications
  // =============================================

  /**
   * Create notification channel for Android O and above
   * Sets up the notification channel with proper importance and settings
   */
  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notifManager = notificationManager
      if (notifManager == null) {
        println("❌ NotificationManager is null, cannot create channel")
        return
      }
      
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "Media Control",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Controls for media playback"
        setShowBadge(false)
        setSound(null, null)
      }
      
      notifManager.createNotificationChannel(channel)
      println("🤖 Notification channel created")
    }
  }

  /**
   * Update or create media notification
   * Builds and displays notification with current metadata and playback state
   */
  private fun updateNotification() {
    if (!isControlsEnabled || mediaSession == null) return
    
    try {
      val context = appContext.reactContext
      val notifManager = notificationManager
      
      if (context == null || notifManager == null) {
        println("❌ Context or NotificationManager is null, cannot update notification")
        return
      }
      
      val sessionToken = mediaSession!!.sessionToken
      
      // Create notification with media style
      val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setStyle(
          MediaNotificationCompat.MediaStyle()
            .setMediaSession(sessionToken)
            .setShowActionsInCompactView() // No custom actions for now
        )
        .setContentTitle(currentMetadata["title"]?.toString() ?: "Unknown Title")
        .setContentText(currentMetadata["artist"]?.toString() ?: "Unknown Artist")
        .setSubText(currentMetadata["album"]?.toString())
        .setSmallIcon(getSmallIconResource())
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(currentPlaybackState == PLAYBACK_STATE_PLAYING)
        .setShowWhen(false)
      
      // Add actions based on current state (simplified for now)
      addNotificationActions(builder)
      
      // Try to load and set artwork for notification
      val artworkMap = currentMetadata["artwork"] as? Map<String, Any>
      val artworkUri = artworkMap?.get("uri") as? String
      if (artworkUri != null) {
        GlobalScope.launch(Dispatchers.IO) {
          try {
            val bitmap = loadArtwork(artworkUri)
            if (bitmap != null) {
              builder.setLargeIcon(bitmap)
              println("🤖 Notification artwork set: ${bitmap.width}x${bitmap.height}")
            }
            withContext(Dispatchers.Main) {
              notifManager.notify(NOTIFICATION_ID, builder.build())
            }
          } catch (e: Exception) {
            println("❌ Failed to load notification artwork: ${e.message}")
            notifManager.notify(NOTIFICATION_ID, builder.build())
          }
        }
      } else {
        notifManager.notify(NOTIFICATION_ID, builder.build())
      }
      
      println("🤖 Notification updated successfully")
    } catch (e: Exception) {
      println("❌ Failed to update notification: ${e.message}")
      e.printStackTrace()
    }
  }

  /**
   * Add action buttons to notification
   * Adds appropriate action buttons based on current playback state
   */
  private fun addNotificationActions(builder: NotificationCompat.Builder) {
    // For now, let's not add custom actions to avoid PendingIntent issues
    // The MediaStyle notification will still show basic controls from the MediaSession
    println("🤖 Notification actions skipped to prevent crashes")
  }

  /**
   * Create intent for media action
   * Creates an intent that can be used for notification actions
   */
  private fun createMediaActionIntent(action: String): Intent {
    return Intent("expo.modules.mediacontrol.MEDIA_ACTION").apply {
      putExtra("action", action)
    }
  }

  /**
   * Get PendingIntent flags based on Android version
   * Returns appropriate flags for PendingIntent creation
   */
  private fun getPendingIntentFlags(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
  }

  /**
   * Get small icon resource ID
   * Returns the resource ID for the notification small icon
   */
  private fun getSmallIconResource(): Int {
    val context = appContext.reactContext
    if (context == null) {
      println("❌ React context is null, using default icon")
      return android.R.drawable.ic_media_play
    }
    
    val notificationConfig = controlOptions["notification"] as? Map<String, Any>
    val iconName = notificationConfig?.get("icon") as? String
    
    return if (iconName != null) {
      try {
        val resourceId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
        if (resourceId != 0) resourceId else android.R.drawable.ic_media_play
      } catch (e: Exception) {
        println("❌ Failed to find icon resource: $iconName")
        android.R.drawable.ic_media_play // Fallback
      }
    } else {
      android.R.drawable.ic_media_play // Default icon
    }
  }

  // =============================================
  // AUDIO FOCUS MANAGEMENT
  // Methods for requesting and managing audio focus
  // =============================================

  /**
   * Check if audio focus should be requested
   * Returns whether audio focus management is enabled in configuration
   */
  private fun shouldRequestAudioFocus(): Boolean {
    val androidConfig = controlOptions["android"] as? Map<String, Any>
    return androidConfig?.get("requestAudioFocus") as? Boolean ?: true
  }

  /**
   * Request audio focus for media playback
   * Requests audio focus using the appropriate API for the Android version
   */
  private fun requestAudioFocus() {
    if (hasAudioFocus) return
    
    val audioMgr = audioManager
    if (audioMgr == null) {
      println("❌ AudioManager is null, cannot request audio focus")
      return
    }
    
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
      
      audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(audioFocusChangeListener)
        .build()
      
      audioMgr.requestAudioFocus(audioFocusRequest!!)
    } else {
      @Suppress("DEPRECATION")
      audioMgr.requestAudioFocus(
        audioFocusChangeListener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN
      )
    }
    
    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    if (hasAudioFocus) {
      println("🤖 Audio focus granted")
    } else {
      println("❌ Audio focus denied")
    }
  }

  /**
   * Release audio focus
   * Releases any held audio focus
   */
  private fun releaseAudioFocus() {
    if (!hasAudioFocus) return
    
    val audioMgr = audioManager
    if (audioMgr == null) {
      println("❌ AudioManager is null, cannot release audio focus")
      return
    }
    
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
      audioMgr.abandonAudioFocusRequest(audioFocusRequest!!)
    } else {
      @Suppress("DEPRECATION")
      audioMgr.abandonAudioFocus(audioFocusChangeListener)
    }
    
    hasAudioFocus = false
    audioFocusRequest = null
    
    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      println("🤖 Audio focus released")
    }
  }

  /**
   * Audio focus change listener
   * Handles changes in audio focus and sends appropriate events
   */
  private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    when (focusChange) {
      AudioManager.AUDIOFOCUS_LOSS -> {
        // Permanent loss of audio focus - pause playback
        sendEvent("audioInterruption", mapOf(
          "type" to "begin",
          "category" to "unknown",
          "shouldResume" to false
        ))
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        // Temporary loss of audio focus - pause but can resume
        sendEvent("audioInterruption", mapOf(
          "type" to "begin", 
          "category" to "transient",
          "shouldResume" to true
        ))
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        // Can continue playing at lower volume
        sendEvent("audioInterruption", mapOf(
          "type" to "begin",
          "category" to "duck",
          "shouldResume" to true
        ))
      }
      AudioManager.AUDIOFOCUS_GAIN -> {
        // Audio focus regained
        sendEvent("audioInterruption", mapOf(
          "type" to "end",
          "shouldResume" to true
        ))
      }
    }
  }

  // =============================================
  // UTILITY METHODS
  // Helper methods for configuration and artwork handling
  // =============================================

  /**
   * Get skip interval from configuration
   * Returns the configured skip interval or default value
   */
  private fun getSkipInterval(): Double {
    val androidConfig = controlOptions["android"] as? Map<String, Any>
    return (androidConfig?.get("skipInterval") as? Number)?.toDouble() ?: 15.0
  }

  /**
   * Load artwork from URI
   * Handles both local and remote artwork loading with proper error handling
   */
  private fun loadArtwork(uri: String): Bitmap? {
    return try {
      if (uri.startsWith("http://") || uri.startsWith("https://")) {
        // Load remote image
        loadRemoteArtwork(uri)
      } else {
        // Load local image
        loadLocalArtwork(uri)
      }
    } catch (e: Exception) {
      println("❌ Failed to load artwork: ${e.message}")
      null
    }
  }

  /**
   * Load artwork from remote URL
   * Downloads and processes remote artwork images with timeout and error handling
   */
  private fun loadRemoteArtwork(uri: String): Bitmap? {
    return try {
      println("🤖 Loading remote artwork: $uri")
      val url = URL(uri)
      val connection = url.openConnection()
      connection.doInput = true
      connection.connectTimeout = 10000 // 10 second timeout
      connection.readTimeout = 10000    // 10 second read timeout
      connection.connect()
      val inputStream = connection.getInputStream()
      val bitmap = BitmapFactory.decodeStream(inputStream)
      inputStream.close()
      
      if (bitmap != null) {
        println("🤖 Remote artwork loaded successfully: ${bitmap.width}x${bitmap.height}")
      } else {
        println("❌ Remote artwork decode returned null")
      }
      
      bitmap
    } catch (e: Exception) {
      println("❌ Failed to load remote artwork from $uri: ${e.message}")
      e.printStackTrace()
      null
    }
  }

  /**
   * Load artwork from local file or assets
   * Loads artwork from local file system or app assets
   */
  private fun loadLocalArtwork(uri: String): Bitmap? {
    val context = appContext.reactContext
    if (context == null) {
      println("❌ React context is null, cannot load local artwork")
      return null
    }
    
    return try {
      when {
        uri.startsWith("file://") -> {
          // Load from file system
          val filePath = uri.substring(7) // Remove "file://" prefix
          BitmapFactory.decodeFile(filePath)
        }
        uri.startsWith("asset://") -> {
          // Load from assets
          val assetPath = uri.substring(8) // Remove "asset://" prefix
          val inputStream = context.assets.open(assetPath)
          BitmapFactory.decodeStream(inputStream)
        }
        else -> {
          // Try to load as resource
          val resourceId = context.resources.getIdentifier(uri, "drawable", context.packageName)
          if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
          } else {
            // Try direct file path
            BitmapFactory.decodeFile(uri)
          }
        }
      }
    } catch (e: Exception) {
      println("❌ Failed to load local artwork: ${e.message}")
      null
    }
  }

  /**
   * Handle remote command events
   * Processes media control commands and sends events to JavaScript
   */
  private fun handleMediaCommand(command: String, data: Map<String, Any>?) {
    val event = mapOf(
      "command" to command,
      "data" to data,
      "timestamp" to System.currentTimeMillis()
    )
    
    sendEvent("mediaControlEvent", event)
    println("🤖 Media command handled: $command")
  }
}
