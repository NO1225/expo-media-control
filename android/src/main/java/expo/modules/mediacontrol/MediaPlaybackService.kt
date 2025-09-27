package expo.modules.mediacontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * MediaBrowserService implementation for background media playback
 * This service provides proper background support and MediaBrowser/MediaController functionality
 * Required for Bluetooth controls and proper system integration
 */
class MediaPlaybackService : MediaBrowserServiceCompat() {

  // Add a flag to track foreground state
  private var isForegroundService = false

  companion object {
    private const val TAG = "MediaPlaybackService"
    private const val NOTIFICATION_CHANNEL_ID = "media_playback_channel"
    private const val NOTIFICATION_ID = 1001
    private const val MEDIA_ROOT_ID = "expo_media_control_root"
    private const val EMPTY_MEDIA_ROOT_ID = "empty_root"
    
    // Action constants for media buttons
    const val ACTION_PLAY = "expo.modules.mediacontrol.PLAY"
    const val ACTION_PAUSE = "expo.modules.mediacontrol.PAUSE"
    const val ACTION_STOP = "expo.modules.mediacontrol.STOP"
    const val ACTION_NEXT = "expo.modules.mediacontrol.NEXT"
    const val ACTION_PREVIOUS = "expo.modules.mediacontrol.PREVIOUS"
    const val ACTION_SKIP_FORWARD = "expo.modules.mediacontrol.SKIP_FORWARD"
    const val ACTION_SKIP_BACKWARD = "expo.modules.mediacontrol.SKIP_BACKWARD"
  }

  // Service binder for local binding
  inner class MediaServiceBinder : Binder() {
    fun getService(): MediaPlaybackService = this@MediaPlaybackService
  }

  private val binder = MediaServiceBinder()
  private lateinit var mediaSession: MediaSessionCompat
  private lateinit var stateBuilder: PlaybackStateCompat.Builder
  private var mediaMetadata: MediaMetadataCompat? = null
  
  // Current state tracking
  private var currentPlaybackState = PlaybackStateCompat.STATE_NONE
  private var currentPosition = 0L
  
  // Notification management
  private val notificationManager: NotificationManager by lazy {
    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  }
  
  // Coroutine scope for async operations
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  
  // Media action broadcast receiver
  private val mediaActionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        ACTION_PLAY -> mediaSessionCallback.onPlay()
        ACTION_PAUSE -> mediaSessionCallback.onPause()
        ACTION_STOP -> mediaSessionCallback.onStop()
        ACTION_NEXT -> mediaSessionCallback.onSkipToNext()
        ACTION_PREVIOUS -> mediaSessionCallback.onSkipToPrevious()
        ACTION_SKIP_FORWARD -> mediaSessionCallback.onFastForward()
        ACTION_SKIP_BACKWARD -> mediaSessionCallback.onRewind()
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    try {
      initializeMediaSession()
      createNotificationChannel()
      registerMediaActionReceiver()
      println("🤖 MediaPlaybackService created successfully")
    } catch (e: Exception) {
      println("❌ Error creating MediaPlaybackService: ${e.message}")
      e.printStackTrace()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
    unregisterReceiver(mediaActionReceiver)
    mediaSession.release()
    
    // Reset foreground state flag
    isForegroundService = false
    println("🤖 MediaPlaybackService destroyed")
  }

  override fun onBind(intent: Intent?): IBinder? {
    return when (intent?.action) {
      SERVICE_INTERFACE -> super.onBind(intent)
      else -> binder
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
      println("🤖 MediaPlaybackService onStartCommand called with action: ${intent?.action}")
      
      // Ensure MediaSession is initialized before handling any intents
      if (!::mediaSession.isInitialized) {
        println("🤖 MediaSession not initialized, initializing now...")
        initializeMediaSession()
      }
      
      // Handle media button events
      if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
        println("🤖 Processing MEDIA_BUTTON intent")
        MediaButtonReceiver.handleIntent(mediaSession, intent)
      } else if (intent != null) {
        // Handle other intents (might be from MediaButtonReceiver)
        MediaButtonReceiver.handleIntent(mediaSession, intent)
      }
      
      // For Android O and above, we need to start foreground service
      // But only if we're not already in foreground
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isForegroundService) {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        isForegroundService = true
        println("🤖 Service started in foreground with notification")
      }
      
    } catch (e: Exception) {
      println("❌ Error in onStartCommand: ${e.message}")
      e.printStackTrace()
    }
    
    return START_STICKY
  }

  // =============================================
  // MediaBrowserService Implementation
  // =============================================

  override fun onGetRoot(
    clientPackageName: String,
    clientUid: Int,
    rootHints: Bundle?
  ): BrowserRoot? {
    // Allow any client to browse (you might want to add authentication here)
    return BrowserRoot(MEDIA_ROOT_ID, null)
  }

  override fun onLoadChildren(
    parentId: String,
    result: Result<MutableList<MediaBrowserCompat.MediaItem>>
  ) {
    // For now, return empty list as we're primarily using this for transport controls
    // In a full implementation, you might return a list of media items
    val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
    result.sendResult(mediaItems)
  }

  // =============================================
  // MediaSession Management
  // =============================================

  private fun initializeMediaSession() {
    // Create MediaSession
    mediaSession = MediaSessionCompat(this, TAG).apply {
      // Set callback for handling transport controls
      setCallback(mediaSessionCallback)
      
      // Configure session flags for proper Bluetooth and system integration
      setFlags(
        MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
      )
      
      // Initialize playback state
      stateBuilder = PlaybackStateCompat.Builder()
        .setActions(getAvailableActions())
        .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
      
      setPlaybackState(stateBuilder.build())
      
      // Set session token for MediaBrowserService
      setSessionToken(sessionToken)
      
      // Activate session
      isActive = true
    }
  }

  private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
    override fun onPlay() {
      try {
        currentPlaybackState = PlaybackStateCompat.STATE_PLAYING
        updatePlaybackState()
        sendEventToModule("play", null)
        
        // Only start foreground service if not already in foreground
        if (!isForegroundService) {
          startForeground(NOTIFICATION_ID, createNotification())
          isForegroundService = true
          println("🤖 Service started in foreground")
        } else {
          // Just update the existing notification
          updateNotification()
          println("🤖 Service already in foreground, updated notification")
        }
      } catch (e: Exception) {
        println("❌ Error in onPlay: ${e.message}")
      }
    }

    override fun onPause() {
      try {
        currentPlaybackState = PlaybackStateCompat.STATE_PAUSED
        updatePlaybackState()
        sendEventToModule("pause", null)
        updateNotification()
      } catch (e: Exception) {
        println("❌ Error in onPause: ${e.message}")
      }
    }

    override fun onStop() {
      try {
        currentPlaybackState = PlaybackStateCompat.STATE_STOPPED
        currentPosition = 0L
        updatePlaybackState()
        sendEventToModule("stop", null)
        
        // Stop foreground service and reset flag
        stopForeground(false)
        isForegroundService = false
        println("🤖 Service stopped from foreground")
        
        stopSelf()
      } catch (e: Exception) {
        println("❌ Error in onStop: ${e.message}")
      }
    }

    override fun onSkipToNext() {
      try {
        sendEventToModule("nextTrack", null)
      } catch (e: Exception) {
        println("❌ Error in onSkipToNext: ${e.message}")
      }
    }

    override fun onSkipToPrevious() {
      try {
        sendEventToModule("previousTrack", null)
      } catch (e: Exception) {
        println("❌ Error in onSkipToPrevious: ${e.message}")
      }
    }

    override fun onSeekTo(pos: Long) {
      try {
        currentPosition = pos
        updatePlaybackState()
        val data = mapOf("position" to (pos / 1000.0))
        sendEventToModule("seek", data)
      } catch (e: Exception) {
        println("❌ Error in onSeekTo: ${e.message}")
      }
    }

    override fun onFastForward() {
      try {
        val skipInterval = 15.0 // Default 15 seconds, could be configurable
        val data = mapOf("interval" to skipInterval)
        sendEventToModule("skipForward", data)
      } catch (e: Exception) {
        println("❌ Error in onFastForward: ${e.message}")
      }
    }

    override fun onRewind() {
      try {
        val skipInterval = 15.0 // Default 15 seconds, could be configurable
        val data = mapOf("interval" to skipInterval)
        sendEventToModule("skipBackward", data)
      } catch (e: Exception) {
        println("❌ Error in onRewind: ${e.message}")
      }
    }

    override fun onSetRating(rating: RatingCompat) {
      try {
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
        sendEventToModule("setRating", data)
      } catch (e: Exception) {
        println("❌ Error in onSetRating: ${e.message}")
      }
    }
  }

  // =============================================
  // Public Interface for Module Integration
  // =============================================

  fun updateMetadata(metadata: Map<String, Any>) {
    val builder = MediaMetadataCompat.Builder()
    
    metadata["title"]?.let { builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.toString()) }
    metadata["artist"]?.let { builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it.toString()) }
    metadata["album"]?.let { builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it.toString()) }
    metadata["genre"]?.let { builder.putString(MediaMetadataCompat.METADATA_KEY_GENRE, it.toString()) }
    metadata["date"]?.let { builder.putString(MediaMetadataCompat.METADATA_KEY_DATE, it.toString()) }
    
    metadata["duration"]?.let {
      val duration = (it as? Number)?.toLong() ?: 0L
      builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration * 1000)
    }
    
    metadata["trackNumber"]?.let {
      val trackNumber = (it as? Number)?.toLong() ?: 0L
      builder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber)
    }
    
    metadata["albumTrackCount"]?.let {
      val trackCount = (it as? Number)?.toLong() ?: 0L
      builder.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, trackCount)
    }

    // Handle artwork
    metadata["artwork"]?.let { artworkData ->
      if (artworkData is Map<*, *>) {
        val uri = artworkData["uri"]?.toString()
        uri?.let { artworkUri ->
          serviceScope.launch {
            try {
              val bitmap = loadArtwork(artworkUri)
              bitmap?.let {
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                mediaMetadata = builder.build()
                mediaSession.setMetadata(mediaMetadata)
                updateNotification()
              }
            } catch (e: Exception) {
              // Fallback without artwork
              mediaMetadata = builder.build()
              mediaSession.setMetadata(mediaMetadata)
              updateNotification()
            }
          }
          return // Exit early to handle async artwork loading
        }
      }
    }
    
    // Set metadata without artwork
    mediaMetadata = builder.build()
    mediaSession.setMetadata(mediaMetadata)
    updateNotification()
  }

  fun updatePlaybackState(state: Int, position: Double?) {
    currentPlaybackState = when (state) {
      0 -> PlaybackStateCompat.STATE_NONE
      1 -> PlaybackStateCompat.STATE_STOPPED
      2 -> PlaybackStateCompat.STATE_PLAYING
      3 -> PlaybackStateCompat.STATE_PAUSED
      4 -> PlaybackStateCompat.STATE_BUFFERING
      5 -> PlaybackStateCompat.STATE_ERROR
      else -> PlaybackStateCompat.STATE_NONE
    }
    
    position?.let {
      currentPosition = (it * 1000).toLong()
    }
    
    updatePlaybackState()
    updateNotification()
  }

  private fun updatePlaybackState() {
    val playbackSpeed = if (currentPlaybackState == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0.0f
    
    stateBuilder
      .setActions(getAvailableActions())
      .setState(currentPlaybackState, currentPosition, playbackSpeed)
    
    mediaSession.setPlaybackState(stateBuilder.build())
  }

  private fun getAvailableActions(): Long {
    return PlaybackStateCompat.ACTION_PLAY or
           PlaybackStateCompat.ACTION_PAUSE or
           PlaybackStateCompat.ACTION_STOP or
           PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
           PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
           PlaybackStateCompat.ACTION_SEEK_TO or
           PlaybackStateCompat.ACTION_FAST_FORWARD or
           PlaybackStateCompat.ACTION_REWIND or
           PlaybackStateCompat.ACTION_SET_RATING
  }

  // =============================================
  // Notification Management
  // =============================================

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "Media Playback",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Controls for media playback"
        setShowBadge(false)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(getSmallIconResource())
      .setContentTitle(mediaMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Unknown")
      .setContentText(mediaMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "Unknown Artist")
      .setLargeIcon(mediaMetadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(currentPlaybackState == PlaybackStateCompat.STATE_PLAYING)
      .setShowWhen(false)

    // Add media style
    builder.setStyle(
      MediaNotificationCompat.MediaStyle()
        .setMediaSession(mediaSession.sessionToken)
        .setShowActionsInCompactView(0, 1, 2)
        .setCancelButtonIntent(createPendingIntent(ACTION_STOP))
        .setShowCancelButton(true)
    )

    // Add actions
    addNotificationActions(builder)

    return builder.build()
  }

  private fun updateNotification() {
    try {
      if (currentPlaybackState != PlaybackStateCompat.STATE_NONE) {
        serviceScope.launch {
          try {
            val notification = withContext(Dispatchers.IO) {
              createNotification()
            }
            withContext(Dispatchers.Main) {
              notificationManager.notify(NOTIFICATION_ID, notification)
            }
          } catch (e: Exception) {
            println("❌ Error updating notification: ${e.message}")
          }
        }
      }
    } catch (e: Exception) {
      println("❌ Error in updateNotification: ${e.message}")
    }
  }

  private fun addNotificationActions(builder: NotificationCompat.Builder) {
    // Previous
    builder.addAction(
      android.R.drawable.ic_media_previous,
      "Previous",
      createPendingIntent(ACTION_PREVIOUS)
    )

    // Play/Pause
    if (currentPlaybackState == PlaybackStateCompat.STATE_PLAYING) {
      builder.addAction(
        android.R.drawable.ic_media_pause,
        "Pause",
        createPendingIntent(ACTION_PAUSE)
      )
    } else {
      builder.addAction(
        android.R.drawable.ic_media_play,
        "Play",
        createPendingIntent(ACTION_PLAY)
      )
    }

    // Next
    builder.addAction(
      android.R.drawable.ic_media_next,
      "Next",
      createPendingIntent(ACTION_NEXT)
    )
  }

  private fun createPendingIntent(action: String): PendingIntent {
    val intent = Intent(action).apply {
      setPackage(packageName)
    }
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
    return PendingIntent.getBroadcast(this, 0, intent, flags)
  }

  private fun getSmallIconResource(): Int {
    // Try to use app's launcher icon, fallback to system icon
    return try {
      packageManager.getApplicationInfo(packageName, 0).icon
    } catch (e: Exception) {
      android.R.drawable.ic_media_play
    }
  }

  // =============================================
  // Module Integration
  // =============================================

  private fun registerMediaActionReceiver() {
    val filter = IntentFilter().apply {
      addAction(ACTION_PLAY)
      addAction(ACTION_PAUSE)
      addAction(ACTION_STOP)
      addAction(ACTION_NEXT)
      addAction(ACTION_PREVIOUS)
      addAction(ACTION_SKIP_FORWARD)
      addAction(ACTION_SKIP_BACKWARD)
    }
    registerReceiver(mediaActionReceiver, filter)
  }

  private fun sendEventToModule(command: String, data: Map<String, Any>?) {
    // This will be called by the ExpoMediaControlModule to handle events
    // The module should register a listener to receive these events
    try {
      ExpoMediaControlModule.handleMediaEvent(command, data)
    } catch (e: Exception) {
      println("❌ Error sending event to module: ${e.message}")
      // Don't crash if module isn't ready yet
    }
  }

  // =============================================
  // Utility Methods
  // =============================================

  private suspend fun loadArtwork(uri: String): Bitmap? {
    return try {
      withContext(Dispatchers.IO) {
        if (uri.startsWith("http")) {
          // Load remote artwork on IO thread (simplified - you might want to add caching)
          val url = java.net.URL(uri)
          val connection = url.openConnection()
          connection.connectTimeout = 5000 // 5 second timeout
          connection.readTimeout = 5000    // 5 second read timeout
          android.graphics.BitmapFactory.decodeStream(connection.getInputStream())
        } else {
          // Load local artwork
          contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { inputStream ->
            android.graphics.BitmapFactory.decodeStream(inputStream)
          }
        }
      }
    } catch (e: Exception) {
      println("❌ Failed to load artwork: ${e.message}")
      null
    }
  }

  fun getMediaSession(): MediaSessionCompat = mediaSession
}