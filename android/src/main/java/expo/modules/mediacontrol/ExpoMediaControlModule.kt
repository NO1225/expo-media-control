package expo.modules.mediacontrol

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Expo Media Control Module for Android
 * 
 * This module provides media control functionality for Android applications through
 * MediaSessionCompat integration and background service support. It handles system media
 * controls, lock screen integration, and remote control events.
 * 
 * Note: This module serves as a bridge between JavaScript and the MediaPlaybackService.
 * All media playback, notification management, and artwork loading is handled by the service.
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
      try {
        val binder = service as MediaPlaybackService.MediaServiceBinder
        mediaService = binder.getService()
        mediaSession = mediaService?.getMediaSession()
        isServiceBound = true
        println("🤖 MediaPlaybackService connected")
        
        // Apply any pending metadata or state updates
        moduleScope.launch {
          try {
            // Update configuration
            val androidConfig = controlOptions["android"] as? Map<String, Any>
            if (androidConfig != null) {
              mediaService?.updateConfiguration(androidConfig)
            }
            
            if (currentMetadata.isNotEmpty()) {
              mediaService?.updateMetadata(currentMetadata.toMap())
            }
            mediaService?.updatePlaybackState(currentPlaybackState, currentPosition.toDouble())
          } catch (e: Exception) {
            println("⚠️ Error applying pending updates after service connection: ${e.message}")
          }
        }
      } catch (e: Exception) {
        println("❌ Error in onServiceConnected: ${e.message}")
        isServiceBound = false
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      println("🤖 MediaPlaybackService disconnected")
      mediaService = null
      mediaSession = null
      isServiceBound = false
    }
    
    override fun onBindingDied(name: ComponentName?) {
      println("⚠️ MediaPlaybackService binding died")
      mediaService = null
      mediaSession = null
      isServiceBound = false
      // Attempt to reconnect
      val connectionRef = this
      moduleScope.launch {
        try {
          val context = appContext.reactContext
          if (context != null && isControlsEnabled) {
            val serviceIntent = Intent(context, MediaPlaybackService::class.java)
            context.bindService(serviceIntent, connectionRef, Context.BIND_AUTO_CREATE)
          }
        } catch (e: Exception) {
          println("❌ Failed to reconnect to service: ${e.message}")
        }
      }
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
    Events("mediaControlEvent", "volumeChange")
    
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
      
      // Track if service binding fails
      var serviceBindFailed = false
      
      // Create and bind to MediaPlaybackService asynchronously to avoid ANR
      moduleScope.launch {
        try {
          val serviceIntent = Intent(context, MediaPlaybackService::class.java)
          
          // Attempt to start the service first - this ensures MediaButtonReceiver can find it
          // Handle Android's background service restrictions gracefully
          withContext(Dispatchers.Main) {
            try {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Check if we can start foreground service
                if (canStartForegroundService(context)) {
                  context.startForegroundService(serviceIntent)
                  println("🤖 Foreground service started successfully")
                } else {
                  println("⚠️ Cannot start foreground service, app may be in background - will bind only")
                  // Don't start service, just bind - this prevents the crash
                }
              } else {
                context.startService(serviceIntent)
                println("🤖 Regular service started successfully")
              }
            } catch (e: SecurityException) {
              println("⚠️ SecurityException starting service: ${e.message}")
              // Continue with binding only - the service might still work
            } catch (e: IllegalStateException) {
              println("⚠️ IllegalStateException starting service: ${e.message}")
              // Continue with binding only
            } catch (e: Exception) {
              println("⚠️ Exception starting service: ${e.message}")
              // Continue with binding only
            }
          }
          
          // Small delay to allow service to start and initialize its MediaSession  
          delay(200)
          
          // Then bind to it for communication
          // Use BIND_AUTO_CREATE to create the service if it wasn't started
          val bindingSuccessful = withContext(Dispatchers.Main) {
            val bindResult = context.bindService(
              serviceIntent, 
              serviceConnection, 
              Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
            )
            if (!bindResult) {
              println("❌ Failed to bind to MediaPlaybackService with BIND_IMPORTANT, trying basic binding")
              // Fallback to basic binding
              val fallbackResult = context.bindService(
                serviceIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
              )
              if (!fallbackResult) {
                println("❌ Failed to bind to MediaPlaybackService - media controls will not work")
                false // Return false to indicate failure
              } else {
                println("🤖 Service binding initiated with fallback")
                true // Return true to indicate success
              }
            } else {
              println("🤖 Service binding initiated")
              true // Return true to indicate success
            }
          }
          
          // Only continue if binding was successful
          if (bindingSuccessful) {
            // Wait a bit more for service connection to complete
            delay(100)
            
            // Initialize/get MediaSession from service
            withContext(Dispatchers.Main) {
              initializeMediaSession()
            }
          }
          
        } catch (e: Exception) {
          println("❌ Failed to start/bind service: ${e.message} - media controls will not work")
          // Don't throw or create fallback - just fail silently
          serviceBindFailed = true
        }
      }
      
      // Only mark controls as enabled if service binding succeeded
      if (!serviceBindFailed) {
        isControlsEnabled = true
        println("🤖 Media controls enabled successfully with service")
      }
    } catch (e: Exception) {
      println("❌ Failed to enable media controls: ${e.message}")
      e.printStackTrace()
      throw e
    }
  }

  /**
   * Disable media controls implementation
   * Unbinds from service and cleans up resources asynchronously to avoid ANR
   */
  private fun disableMediaControls() {
    try {
      // Mark as disabled immediately to prevent new operations
      isControlsEnabled = false
      
      // Perform cleanup asynchronously to avoid blocking the main thread
      moduleScope.launch {
        try {
          val context = appContext.reactContext
          
          // Unbind from service
          withContext(Dispatchers.Main) {
            try {
              if (isServiceBound && context != null) {
                context.unbindService(serviceConnection)
                isServiceBound = false
                println("🤖 Service unbound successfully")
              }
            } catch (e: Exception) {
              println("⚠️ Error unbinding service: ${e.message}")
            }
          }
          
          // Stop the service
          withContext(Dispatchers.Main) {
            try {
              if (context != null) {
                val serviceIntent = Intent(context, MediaPlaybackService::class.java)
                context.stopService(serviceIntent)
                println("🤖 Service stopped successfully")
              }
            } catch (e: Exception) {
              println("⚠️ Error stopping service: ${e.message}")
            }
          }
          
          // Clear references
          mediaService = null
          mediaSession = null
          
          // Reset state
          currentMetadata.clear()
          currentPlaybackState = PLAYBACK_STATE_NONE
          currentPosition = 0L
          controlOptions.clear()
          
          println("🤖 Media controls disabled successfully")
        } catch (e: Exception) {
          println("❌ Error during service cleanup: ${e.message}")
        } finally {
          // Cancel all ongoing coroutines to prevent memory leaks
          try {
            moduleScope.cancel()
            println("🤖 Coroutine scope canceled")
          } catch (e: Exception) {
            println("⚠️ Error canceling coroutine scope: ${e.message}")
          }
        }
      }
      
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
      
      // Only update if service is bound
      if (isServiceBound && mediaService != null) {
        mediaService?.updateMetadata(currentMetadata.toMap())
        println("🤖 Metadata updated via service: ${currentMetadata["title"]} - ${currentMetadata["artist"]}")
      } else {
        println("⚠️ Service not bound, metadata update skipped")
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
      
      // Only update if service is bound
      if (isServiceBound && mediaService != null) {
        mediaService?.updatePlaybackState(state, position)
        println("🤖 Playback state updated via service: $state, position: ${currentPosition}ms")
      } else {
        println("⚠️ Service not bound, playback state update skipped")
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
      
      // Only reset if service is bound
      if (isServiceBound && mediaService != null) {
        mediaService?.updateMetadata(emptyMap())
        mediaService?.updatePlaybackState(PLAYBACK_STATE_NONE, 0.0)
        println("🤖 Controls reset via service to initial state")
      } else {
        println("⚠️ Service not bound, controls reset skipped")
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
   * Initialize MediaSession - Now using the service's MediaSession instead of creating our own
   * This prevents conflicts with MediaButtonReceiver which expects one consistent MediaSession
   */
  private fun initializeMediaSession() {
    try {
      // Wait for service to be bound and use its MediaSession
      if (mediaService != null) {
        mediaSession = mediaService!!.getMediaSession()
        println("🤖 Using MediaSession from service")
      } else {
        println("⚠️ Service not yet bound, MediaSession will be initialized when service connects")
      }
    } catch (e: Exception) {
      println("❌ Failed to initialize MediaSession: ${e.message}")
      e.printStackTrace()
    }
  }

  // =============================================
  // UTILITY METHODS
  // =============================================

  /**
   * Check if we can start a foreground service based on current app state
   */
  private fun canStartForegroundService(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return true // No restrictions before Android O
    }
    
    return try {
      // Check if the app is in foreground or has recent user interaction
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      if (activityManager != null) {
        val runningAppProcesses = activityManager.runningAppProcesses
        runningAppProcesses?.any { processInfo ->
          processInfo.processName == context.packageName && 
          processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } ?: false
      } else {
        false
      }
    } catch (e: Exception) {
      println("⚠️ Error checking foreground service eligibility: ${e.message}")
      false
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
