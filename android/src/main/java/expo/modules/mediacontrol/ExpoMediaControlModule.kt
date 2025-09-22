package expo.modules.mediacontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
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
  // PROPERTIES AND STATE MANAGEMENT
  // =============================================
  
  /// MediaSession for handling media controls and state
  private var mediaSession: MediaSessionCompat? = null
  
  /// Service connection and binding
  private var mediaService: MediaPlaybackService? = null
  private var isServiceBound = false
  
  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val binder = service as MediaPlaybackService.MediaServiceBinder
      mediaService = binder.getService()
      mediaSession = mediaService?.getMediaSession()
      isServiceBound = true
      println("🤖 MediaPlaybackService connected")
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      mediaService = null
      mediaSession = null
      isServiceBound = false
      println("🤖 MediaPlaybackService disconnected")
    }
  }
  
  /// Current media metadata
  private var currentMetadata: MutableMap<String, Any> = ConcurrentHashMap()
  
  /// Current playback state
  @Volatile
  private var currentPlaybackState: Int = PLAYBACK_STATE_NONE
  
  /// Current playback position in milliseconds
  @Volatile
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
  
  /// Coroutine scope for managing async operations with proper lifecycle
  private var moduleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  // =============================================
  // COMPANION OBJECT FOR STATIC ACCESS
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
    
    // Static reference for service communication
    private var moduleInstance: ExpoMediaControlModule? = null
    
    @JvmStatic
    fun handleMediaEvent(command: String, data: Map<String, Any>?) {
      try {
        moduleInstance?.handleMediaCommand(command, data)
      } catch (e: Exception) {
        println("❌ Error handling media event: ${e.message}")
        // Don't crash if there's an issue with event handling
      }
    }
  }

  // =============================================
  // MODULE DEFINITION
  // =============================================
  
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module
    Name("ExpoMediaControl")

    // Module lifecycle callbacks
    OnCreate {
      try {
        moduleInstance = this@ExpoMediaControlModule
        println("🤖 ExpoMediaControl module created")
      } catch (e: Exception) {
        println("❌ Error during module creation: ${e.message}")
      }
    }
    
    OnDestroy {
      try {
        if (isControlsEnabled) {
          disableMediaControls()
        }
        moduleInstance = null
        println("🤖 ExpoMediaControl module destroyed")
      } catch (e: Exception) {
        println("❌ Error during module cleanup: ${e.message}")
      }
    }

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
    
    // =============================================
    // LIFECYCLE MANAGEMENT
    // Ensure proper cleanup when module is destroyed
    // =============================================
    
    OnDestroy {
      try {
        if (isControlsEnabled) {
          disableMediaControls()
        }
        println("🤖 ExpoMediaControl module destroyed and cleaned up")
      } catch (e: Exception) {
        println("⚠️ Error during module cleanup: ${e.message}")
      }
    }
  }

  // =============================================
  // IMPLEMENTATION METHODS
  // Private methods that implement the actual functionality
  // =============================================

  /**
   * Enable media controls with the specified configuration options
   * Starts and binds to MediaPlaybackService for proper background support
   */
  private fun enableMediaControls(options: Map<String, Any>) {
    try {
      // Recreate coroutine scope to ensure it's fresh
      try {
        moduleScope.cancel() // Cancel any existing scope
      } catch (e: Exception) {
        // Ignore errors when canceling
      }
      moduleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
      
      // Store configuration options
      controlOptions.clear()
      controlOptions.putAll(options)
      
      val context = appContext.reactContext
      if (context == null) {
        println("❌ React context is null, cannot enable media controls")
        throw Exception("React context is null")
      }
      
      // Create and bind to MediaPlaybackService
      val serviceIntent = Intent(context, MediaPlaybackService::class.java)
      
      // Start the service first
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
      } else {
        context.startService(serviceIntent)
      }
      
      // Then bind to it for communication
      val bindResult = context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
      if (!bindResult) {
        println("❌ Failed to bind to MediaPlaybackService")
        throw Exception("Failed to bind to MediaPlaybackService")
      }
      
      // Mark controls as enabled
      isControlsEnabled = true
      
      println("🤖 Media controls enabled successfully with service")
    } catch (e: Exception) {
      println("❌ Failed to enable media controls: ${e.message}")
      e.printStackTrace()
      throw e
    }
  }

  /**
   * Disable media controls implementation
   * Unbinds from service and cleans up resources
   */
  private fun disableMediaControls() {
    try {
      // Cancel all ongoing coroutines to prevent memory leaks
      try {
        moduleScope.cancel()
        println("🤖 Coroutine scope canceled")
      } catch (e: Exception) {
        println("⚠️ Error canceling coroutine scope: ${e.message}")
      }
      
      val context = appContext.reactContext
      
      // Unbind from service
      try {
        if (isServiceBound && context != null) {
          context.unbindService(serviceConnection)
          isServiceBound = false
          println("🤖 Service unbound successfully")
        }
      } catch (e: Exception) {
        println("⚠️ Error unbinding service: ${e.message}")
      }
      
      // Stop the service
      try {
        if (context != null) {
          val serviceIntent = Intent(context, MediaPlaybackService::class.java)
          context.stopService(serviceIntent)
          println("🤖 Service stopped successfully")
        }
      } catch (e: Exception) {
        println("⚠️ Error stopping service: ${e.message}")
      }
      
      // Clear references
      mediaService = null
      mediaSession = null
      
      // Reset state
      isControlsEnabled = false
      currentMetadata.clear()
      currentPlaybackState = PLAYBACK_STATE_NONE
      currentPosition = 0L
      controlOptions.clear()
      
      println("🤖 Media controls disabled successfully")
    } catch (e: Exception) {
      println("❌ Failed to disable media controls: ${e.message}")
      e.printStackTrace()
      throw e
    }
  }

  /**
   * Update metadata implementation
   * Delegates to MediaPlaybackService for proper MediaSession management
   */
  private fun updateMetadata(metadata: Map<String, Any>) {
    try {
      // Store current metadata with thread-safe access
      synchronized(currentMetadata) {
        currentMetadata.clear()
        
        // Clean and validate metadata before storing
        val cleanMetadata = mutableMapOf<String, Any>()
        metadata.forEach { (key, value) ->
          when {
            value is String -> cleanMetadata[key] = value
            value is Number -> cleanMetadata[key] = value
            value is Boolean -> cleanMetadata[key] = value
            value is Map<*, *> -> {
              // Handle nested maps (like artwork)
              val nestedMap = mutableMapOf<String, Any>()
              value.forEach { (nestedKey, nestedValue) ->
                if (nestedKey is String && nestedValue != null) {
                  when (nestedValue) {
                    is String -> nestedMap[nestedKey] = nestedValue
                    is Number -> nestedMap[nestedKey] = nestedValue
                    is Boolean -> nestedMap[nestedKey] = nestedValue
                  }
                }
              }
              if (nestedMap.isNotEmpty()) {
                cleanMetadata[key] = nestedMap
              }
            }
            else -> {
              // Skip invalid types but log them
              println("⚠️ Skipping metadata field '$key' with unsupported type: ${value?.javaClass?.simpleName}")
            }
          }
        }
        
        currentMetadata.putAll(cleanMetadata)
      }
      
      // Delegate to service if bound
      if (isServiceBound) {
        mediaService?.updateMetadata(currentMetadata.toMap())
        println("🤖 Metadata updated via service: ${currentMetadata["title"]} - ${currentMetadata["artist"]}")
      } else {
        println("⚠️ Service not bound, cannot update metadata")
      }
    } catch (e: Exception) {
      println("❌ Failed to update metadata: ${e.message}")
      e.printStackTrace()
      throw e
    }
  }

  /**
   * Update playback state implementation
   * Delegates to MediaPlaybackService for proper state management
   */
  private fun updatePlaybackState(state: Int, position: Double?) {
    try {
      currentPlaybackState = state
      
      // Update position if provided
      position?.let { 
        currentPosition = (it * 1000).toLong() // Convert to milliseconds
      }
      
      // Delegate to service if bound
      if (isServiceBound) {
        mediaService?.updatePlaybackState(state, position)
        println("🤖 Playback state updated via service: $state, position: ${currentPosition}ms")
      } else {
        println("⚠️ Service not bound, cannot update playback state")
      }
    } catch (e: Exception) {
      println("❌ Failed to update playback state: ${e.message}")
      e.printStackTrace()
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
      
      // Reset via service if bound
      if (isServiceBound) {
        // Reset metadata and state via service
        mediaService?.updateMetadata(emptyMap())
        mediaService?.updatePlaybackState(PLAYBACK_STATE_NONE, 0.0)
        println("🤖 Controls reset via service to initial state")
      } else {
        println("⚠️ Service not bound, cannot reset controls")
      }
    } catch (e: Exception) {
      println("❌ Failed to reset controls: ${e.message}")
      e.printStackTrace()
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
   * Thread-safe: always executes on main thread with synchronized data access
   */
  private fun updateNotification() {
    if (!isControlsEnabled || mediaSession == null) return
    
    // Ensure we're on the main thread for UI operations
    if (Thread.currentThread() != Looper.getMainLooper().thread) {
      moduleScope.launch(Dispatchers.Main) {
        updateNotification()
      }
      return
    }
    
    try {
      val context = appContext.reactContext
      val notifManager = notificationManager
      
      if (context == null || notifManager == null) {
        println("❌ Context or NotificationManager is null, cannot update notification")
        return
      }
      
      val sessionToken = mediaSession!!.sessionToken
      
      // Take a synchronized snapshot of current state to avoid race conditions
      val metadataSnapshot = synchronized(currentMetadata) { 
        currentMetadata.toMap() 
      }
      val playbackStateSnapshot = currentPlaybackState
      
      // Create notification with media style using synchronized data
      val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setStyle(
          MediaNotificationCompat.MediaStyle()
            .setMediaSession(sessionToken)
            .setShowActionsInCompactView() // No custom actions for now
        )
        .setContentTitle(metadataSnapshot["title"]?.toString() ?: "Unknown Title")
        .setSmallIcon(getSmallIconResource())
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(playbackStateSnapshot == PLAYBACK_STATE_PLAYING)
        .setShowWhen(false)
      
      // Set artist only if provided
      metadataSnapshot["artist"]?.toString()?.let { artist ->
        if (artist.isNotBlank()) {
          builder.setContentText(artist)
        }
      }
      
      // Set album only if provided  
      metadataSnapshot["album"]?.toString()?.let { album ->
        if (album.isNotBlank()) {
          builder.setSubText(album)
        }
      }
      
      // Add actions based on current state (simplified for now)
      addNotificationActions(builder)
      
      // Try to load and set artwork for notification
      val artworkMap = metadataSnapshot["artwork"] as? Map<String, Any>
      val artworkUri = artworkMap?.get("uri") as? String
      if (artworkUri != null) {
        moduleScope.launch(Dispatchers.IO) {
          try {
            val bitmap = loadArtwork(artworkUri)
            withContext(Dispatchers.Main) {
              try {
                if (bitmap != null) {
                  builder.setLargeIcon(bitmap)
                  println("🤖 Notification artwork set: ${bitmap.width}x${bitmap.height}")
                }
                notifManager.notify(NOTIFICATION_ID, builder.build())
              } catch (e: Exception) {
                println("❌ Failed to update notification with artwork: ${e.message}")
                // Try without artwork
                try {
                  notifManager.notify(NOTIFICATION_ID, builder.build())
                } catch (e2: Exception) {
                  println("❌ Failed to show notification at all: ${e2.message}")
                }
              }
            }
          } catch (e: Exception) {
            println("❌ Failed to load notification artwork: ${e.message}")
            withContext(Dispatchers.Main) {
              try {
                notifManager.notify(NOTIFICATION_ID, builder.build())
              } catch (e2: Exception) {
                println("❌ Failed to show notification: ${e2.message}")
              }
            }
          }
        }
      } else {
        try {
          notifManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
          println("❌ Failed to show notification: ${e.message}")
        }
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
  fun handleMediaCommand(command: String, data: Map<String, Any>?) {
    val event = mapOf(
      "command" to command,
      "data" to data,
      "timestamp" to System.currentTimeMillis()
    )
    
    sendEvent("mediaControlEvent", event)
    println("🤖 Media command handled: $command")
  }
}
