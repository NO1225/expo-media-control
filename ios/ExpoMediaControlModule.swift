import ExpoModulesCore
import MediaPlayer
import AVFoundation

/**
 * Expo Media Control Module for iOS
 * 
 * This module provides comprehensive media control functionality for iOS applications,
 * including integration with Control Center, Lock Screen controls, and remote control events.
 * It handles MPNowPlayingInfoCenter updates, MPRemoteCommandCenter registration,
 * audio session management, and background audio playback support.
 */
public class ExpoMediaControlModule: Module {
  // =============================================
  // PROPERTIES AND STATE MANAGEMENT
  // =============================================
  
  /// Current media metadata being displayed
  private var currentMetadata: [String: Any] = [:]
  
  /// Current playback state
  private var currentPlaybackState: Int = 0 // PlaybackState.NONE
  
  /// Current playback position in seconds
  private var currentPosition: Double = 0.0

  /// Current playback rate/speed (1.0 = normal speed, 2.0 = 2x speed, etc.)
  private var currentPlaybackRate: Double = 1.0

  /// Whether media controls are currently enabled
  private var isControlsEnabled: Bool = false
  
  /// Configuration options for the media controls
  private var controlOptions: [String: Any] = [:]
  
  /// Whether rating controls are currently available
  private var isRatingEnabled: Bool = false

  /// Enabled capabilities (nil = all enabled for backward compatibility)
  private var enabledCapabilities: [String]? = nil

  /// Incremented on every metadata update so late artwork loads for an older track are discarded
  private var metadataGeneration: Int = 0
  
  /// Remote command center reference for managing remote controls
  private var remoteCommandCenter: MPRemoteCommandCenter {
    return MPRemoteCommandCenter.shared()
  }
  
  /// Now playing info center for updating media information
  private var nowPlayingInfoCenter: MPNowPlayingInfoCenter {
    return MPNowPlayingInfoCenter.default()
  }
  
  /// Audio session for managing audio playback
  private var audioSession: AVAudioSession {
    return AVAudioSession.sharedInstance()
  }

  // =============================================
  // MODULE DEFINITION
  // =============================================
  
  public func definition() -> ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module
    Name("ExpoMediaControl")

    // =============================================
    // MAIN CONTROL METHODS
    // Methods for enabling/disabling and managing media controls
    // =============================================
    
    /**
     * Enable media controls with specified configuration
     * Sets up the audio session, registers remote command handlers, and prepares the system
     * for media control integration
     */
    AsyncFunction("enableMediaControls") { (options: [String: Any]?) in
      return try await self.enableMediaControls(options: options)
    }

    /**
     * Disable media controls and clean up all resources
     * Removes all remote command handlers, resets audio session, and cleans up state
     */
    AsyncFunction("disableMediaControls") {
      return try await self.disableMediaControls()
    }

    /**
     * Update the media metadata displayed in system controls
     * Updates Control Center, Lock Screen, and other system UI with current track information
     */
    AsyncFunction("updateMetadata") { (metadata: [String: Any]) in
      return try await self.updateMetadata(metadata: metadata)
    }

    /**
     * Update the current playback state and position
     * Informs the system about current playback status for proper UI updates
     * @param state - The playback state
     * @param position - The current position in seconds (optional)
     * @param playbackRate - The playback rate/speed (optional)
     */
    AsyncFunction("updatePlaybackState") { (state: Int, position: Double?, playbackRate: Double?) in
      return try await self.updatePlaybackState(state: state, position: position, playbackRate: playbackRate)
    }

    /**
     * Reset all media control information to default state
     * Clears all metadata and resets playback state to initial values
     */
    AsyncFunction("resetControls") {
      return try await self.resetControls()
    }

    // =============================================
    // STATE QUERY METHODS
    // Methods for retrieving current state information
    // =============================================

    /**
     * Check if media controls are currently enabled
     * Returns whether the media session is active and ready to receive events
     */
    AsyncFunction("isEnabled") { () -> Bool in
      return self.isControlsEnabled
    }

    /**
     * Get the current media metadata
     * Returns the currently set metadata information as a dictionary
     */
    AsyncFunction("getCurrentMetadata") { () -> [String: Any]? in
      return self.currentMetadata.isEmpty ? nil : self.currentMetadata
    }

    /**
     * Get the current playback state
     * Returns the current playback status as an integer
     */
    AsyncFunction("getCurrentState") { () -> Int in
      return self.currentPlaybackState
    }

    // =============================================
    // EVENT DEFINITIONS
    // Define events that can be sent to JavaScript
    // =============================================
    
    /// Event fired when media control commands are received (play, pause, next, etc.)
    Events("mediaControlEvent")
  }

  // =============================================
  // IMPLEMENTATION METHODS
  // Private methods that implement the actual functionality
  // =============================================

  /**
   * Enable media controls implementation
   * Sets up audio session, registers command handlers, and prepares for media control
   */
  private func enableMediaControls(options: [String: Any]?) async throws {
    // Already enabled: apply the new configuration (capabilities, skip interval) without
    // touching the audio session, so calling this again reconfigures the controls
    if isControlsEnabled {
      controlOptions = options ?? [:]
      enabledCapabilities = options?["capabilities"] as? [String]
      await MainActor.run {
        unregisterRemoteCommandHandlers()
        registerRemoteCommandHandlers()
        updateRatingCommands()
      }
      print("📱 Media controls reconfigured")
      return
    }
    
    do {
      // Store configuration options
      if let opts = options {
        controlOptions = opts
        enabledCapabilities = opts["capabilities"] as? [String]
      } else {
        enabledCapabilities = nil
      }
      
      // Configure audio session for playback (this might fail with OSStatus -50)
      try await configureAudioSession()
      
      // Register remote command handlers on main thread
      await MainActor.run {
        registerRemoteCommandHandlers()
      }
      
      // Mark controls as enabled
      isControlsEnabled = true
      
      print("📱 Media controls enabled successfully")
    } catch let error as NSError {
      print("❌ Failed to enable media controls: \(error.localizedDescription) (Code: \(error.code))")
      // Clean up partial state
      isControlsEnabled = false
      controlOptions.removeAll()
      throw error
    } catch {
      print("❌ Failed to enable media controls: \(error)")
      // Clean up partial state
      isControlsEnabled = false
      controlOptions.removeAll()
      throw error
    }
  }

  /**
   * Disable media controls implementation
   * Cleans up all handlers, audio session, and resets state
   */
  private func disableMediaControls() async throws {
    // Unregister all remote command handlers
    unregisterRemoteCommandHandlers()
    
    // Stop receiving remote control events
    UIApplication.shared.endReceivingRemoteControlEvents()
    
    // Clear now playing info (and invalidate any in-flight artwork load)
    metadataGeneration += 1
    DispatchQueue.main.async { [weak self] in
      self?.nowPlayingInfoCenter.playbackState = .unknown
      self?.nowPlayingInfoCenter.nowPlayingInfo = nil
    }
    
    // Try to deactivate audio session cleanly
    do {
      try audioSession.setActive(false, options: .notifyOthersOnDeactivation)
      print("📱 Audio session deactivated successfully")
    } catch {
      print("⚠️ Failed to deactivate audio session: \(error)")
      // Don't throw - not critical for cleanup
    }
    
    // Reset state
    isControlsEnabled = false
    currentMetadata.removeAll()
    currentPlaybackState = 0 // PlaybackState.NONE
    currentPosition = 0.0
    currentPlaybackRate = 1.0
    controlOptions.removeAll()
    isRatingEnabled = false
    enabledCapabilities = nil
    
    print("📱 Media controls disabled successfully")
  }

  /**
   * Update metadata implementation
   * Converts metadata dictionary and updates MPNowPlayingInfoCenter
   */
  private func updateMetadata(metadata: [String: Any]) async throws {
    currentMetadata = metadata

    // elapsedTime is a shortcut for passing the position to updatePlaybackState()
    if let elapsedTime = metadata["elapsedTime"] as? Double {
      currentPosition = elapsedTime
    }
    
    // Convert metadata to MPNowPlayingInfoCenter format
    var nowPlayingInfo: [String: Any] = [:]
    
    // Basic information
    if let title = metadata["title"] as? String {
      nowPlayingInfo[MPMediaItemPropertyTitle] = title
    }
    
    if let artist = metadata["artist"] as? String {
      nowPlayingInfo[MPMediaItemPropertyArtist] = artist
    }
    
    if let album = metadata["album"] as? String {
      nowPlayingInfo[MPMediaItemPropertyAlbumTitle] = album
    }

    if let duration = metadata["duration"] as? Double {
      nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = duration
    }

    if let isLiveStream = metadata["isLiveStream"] as? Bool {
      nowPlayingInfo[MPNowPlayingInfoPropertyIsLiveStream] = NSNumber(value: isLiveStream)
    }
    
    if let genre = metadata["genre"] as? String {
      nowPlayingInfo[MPMediaItemPropertyGenre] = genre
    }
    
    if let trackNumber = metadata["trackNumber"] as? Int {
      nowPlayingInfo[MPMediaItemPropertyAlbumTrackNumber] = trackNumber
    }
    
    if let albumTrackCount = metadata["albumTrackCount"] as? Int {
      nowPlayingInfo[MPMediaItemPropertyAlbumTrackCount] = albumTrackCount
    }
    
    // Handle rating metadata
    let hasRating = metadata["rating"] != nil
    if hasRating, let ratingDict = metadata["rating"] as? [String: Any] {
      // Update rating state
      isRatingEnabled = true
      
      // Handle different rating types
      if let ratingType = ratingDict["type"] as? String {
        switch ratingType {
        case "heart":
          if let ratingValue = ratingDict["value"] as? Bool {
            // For heart/like rating, we don't set a specific MPMediaItem property
            // as iOS handles like/dislike through remote commands
            print("📱 Heart rating detected: \(ratingValue)")
          }
        case "thumbsUpDown":
          if let ratingValue = ratingDict["value"] as? Bool {
            print("📱 Thumbs rating detected: \(ratingValue)")
          }
        case "fiveStars", "fourStars", "threeStars":
          if let ratingValue = ratingDict["value"] as? Double,
             let maxValue = ratingDict["maxValue"] as? Double {
            // Convert to 0-1 scale for iOS
            let normalizedRating = ratingValue / maxValue
            nowPlayingInfo[MPMediaItemPropertyRating] = normalizedRating
            print("📱 Star rating detected: \(ratingValue)/\(maxValue)")
          }
        case "percentage":
          if let ratingValue = ratingDict["value"] as? Double {
            // Convert percentage to 0-1 scale
            let normalizedRating = ratingValue / 100.0
            nowPlayingInfo[MPMediaItemPropertyRating] = normalizedRating
            print("📱 Percentage rating detected: \(ratingValue)%")
          }
        default:
          print("📱 Unknown rating type: \(ratingType)")
        }
      }
    } else {
      // No rating provided, disable rating controls
      isRatingEnabled = false
    }
    
    // Update rating commands based on current metadata
    updateRatingCommands()
    
    // Set elapsed time
    nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentPosition

    // Set playback rate - use the stored rate which reflects actual playback speed
    nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = currentPlaybackRate

    metadataGeneration += 1
    let generation = metadataGeneration
    let playbackState = currentPlaybackState

    // Publish text metadata immediately. All Now Playing writes go through the main queue so
    // they are applied in call order (e.g. updateMetadata followed by updatePlaybackState).
    DispatchQueue.main.async { [weak self] in
      self?.nowPlayingInfoCenter.playbackState = self?.resolveNowPlayingPlaybackState(playbackState) ?? .unknown
      self?.nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
    }

    // Load artwork in the background and merge it in when ready, unless a newer track was set meanwhile
    if let artworkDict = metadata["artwork"] as? [String: Any],
       let uri = artworkDict["uri"] as? String {
      Task { [weak self] in
        await self?.loadArtwork(uri: uri) { artwork in
          guard let artwork = artwork else { return }
          DispatchQueue.main.async {
            guard let self = self, generation == self.metadataGeneration else { return }
            var info = self.nowPlayingInfoCenter.nowPlayingInfo ?? [:]
            info[MPMediaItemPropertyArtwork] = artwork
            self.nowPlayingInfoCenter.nowPlayingInfo = info
          }
        }
      }
    }
    
    print("📱 Metadata updated: \(metadata["title"] ?? "Unknown") - \(metadata["artist"] ?? "Unknown")")
  }

  /**
   * Update playback state implementation
   * Updates the system about current playback status
   * @param state - The playback state
   * @param position - The current position in seconds (optional)
   * @param playbackRate - The playback rate/speed (optional)
   */
  private func updatePlaybackState(state: Int, position: Double?, playbackRate: Double?) async throws {
    currentPlaybackState = state

    if let pos = position {
      currentPosition = pos
    }

    // Update playback rate if provided, otherwise use default based on state
    if let rate = playbackRate {
      currentPlaybackRate = rate
    } else {
      // Fallback to default behavior when rate is not provided
      switch state {
      case 2: // PlaybackState.PLAYING
        currentPlaybackRate = 1.0
      case 3, 4: // PlaybackState.PAUSED or BUFFERING
        currentPlaybackRate = 0.0
      default: // NONE, STOPPED, ERROR
        currentPlaybackRate = 0.0
      }
    }

    let position = currentPosition
    let rate = currentPlaybackRate

    // Read-modify-write on the main queue so we never overwrite metadata that was queued
    // by an earlier updateMetadata call with a stale copy of the info dictionary
    DispatchQueue.main.async { [weak self] in
      guard let self = self else { return }
      var nowPlayingInfo = self.nowPlayingInfoCenter.nowPlayingInfo ?? [:]

      // Update elapsed time
      nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = position

      // Update playback rate - use the stored rate which reflects actual playback speed
      // This allows iOS to calculate progress correctly between updates
      nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = rate

      self.nowPlayingInfoCenter.playbackState = self.resolveNowPlayingPlaybackState(state)
      self.nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
    }

    print("📱 Playback state updated: \(state), position: \(currentPosition), rate: \(currentPlaybackRate)")
  }

  /**
   * Resolve module playback state values to iOS' explicit Now Playing state.
   * Setting this separately from the info dictionary helps the system render
   * the correct lock screen and Control Center controls.
   */
  private func resolveNowPlayingPlaybackState(_ state: Int) -> MPNowPlayingPlaybackState {
    switch state {
    case 1: // PlaybackState.STOPPED
      return .stopped
    case 2: // PlaybackState.PLAYING
      return .playing
    case 3: // PlaybackState.PAUSED
      return .paused
    case 4: // PlaybackState.BUFFERING
      return .interrupted
    default:
      return .unknown
    }
  }

  /**
   * Reset controls implementation
   * Clears all information and returns to initial state
   */
  private func resetControls() async throws {
    currentMetadata.removeAll()
    currentPlaybackState = 0 // PlaybackState.NONE
    currentPosition = 0.0
    currentPlaybackRate = 1.0
    isRatingEnabled = false
    
    // Update rating commands to reflect disabled state
    updateRatingCommands()
    
    // Clear now playing info (and invalidate any in-flight artwork load)
    metadataGeneration += 1
    DispatchQueue.main.async { [weak self] in
      self?.nowPlayingInfoCenter.playbackState = .unknown
      self?.nowPlayingInfoCenter.nowPlayingInfo = nil
    }
    
    print("📱 Controls reset to initial state")
  }

  // =============================================
  // AUDIO SESSION MANAGEMENT
  // Methods for configuring and managing the audio session
  // =============================================

  /**
   * Configure audio session for media playback
   * Sets up the audio session category and activates it for background playback
   * Includes comprehensive error handling and fallback strategies
   */
  private func configureAudioSession() async throws {
    try await MainActor.run {
      do {
        // First deactivate any existing session to avoid conflicts
        try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
        
        // Try primary configuration with all features
        try audioSession.setCategory(
          .playback,
          mode: .default,
          options: [.allowBluetooth, .allowBluetoothA2DP, .allowAirPlay]
        )
        
        print("📱 Audio session category set successfully")
        
        // Try to activate with notification to other apps
        try audioSession.setActive(true, options: [])
        
        // Begin receiving remote control events
        UIApplication.shared.beginReceivingRemoteControlEvents()
        
        print("📱 Audio session activated successfully with full features")
        
      } catch let error as NSError where error.domain == NSOSStatusErrorDomain {
        print("⚠️ Audio session error (OSStatus \(error.code)), trying fallbacks...")
        
        do {
          // Fallback 1: Try without Bluetooth options
          try audioSession.setCategory(.playback, mode: .default, options: [.allowAirPlay])
          try audioSession.setActive(true, options: [])
          UIApplication.shared.beginReceivingRemoteControlEvents()
          
          print("📱 Audio session configured with fallback 1 (no Bluetooth)")
          
        } catch {
          print("⚠️ Fallback 1 failed, trying minimal configuration...")
          
          do {
            // Fallback 2: Minimal configuration
            try audioSession.setCategory(.playback, mode: .default, options: [])
            try audioSession.setActive(true, options: [])
            UIApplication.shared.beginReceivingRemoteControlEvents()
            
            print("📱 Audio session configured with minimal settings")
            
          } catch {
            print("⚠️ Minimal configuration failed, trying last resort...")
            
            // Fallback 3: Just set category without activation (for apps that manage their own session)
            try audioSession.setCategory(.playback)
            UIApplication.shared.beginReceivingRemoteControlEvents()
            
            print("📱 Audio session category set without activation")
          }
        }
        
      } catch let error as NSError {
        print("❌ Failed to configure audio session: \(error.localizedDescription)")
        print("   Domain: \(error.domain), Code: \(error.code)")
        
        // Last resort: try to at least enable remote control events
        do {
          UIApplication.shared.beginReceivingRemoteControlEvents()
          print("📱 At least remote control events are enabled")
        } catch {
          print("❌ Complete audio session configuration failure")
        }
        
        // Don't throw here - allow module to work without perfect audio session
        print("⚠️ Continuing with imperfect audio session setup")
        
      } catch {
        print("❌ Unexpected error configuring audio session: \(error)")
        
        // Try minimal setup
        do {
          UIApplication.shared.beginReceivingRemoteControlEvents()
          print("📱 Remote control events enabled despite session error")
        } catch {
          print("❌ Total audio session failure")
        }
        
        // Don't throw - be resilient
        print("⚠️ Continuing despite audio session issues")
      }
    }
  }

  // =============================================
  // REMOTE COMMAND HANDLING
  // Methods for registering and handling remote control commands
  // =============================================

  /**
   * Check if a capability is enabled
   * Returns true if enabledCapabilities is nil (all enabled) or contains the command
   */
  private func isCapabilityEnabled(_ command: String) -> Bool {
    guard let caps = enabledCapabilities else { return true }
    return caps.contains(command)
  }

  /**
   * Register all remote command handlers
   * Sets up handlers for play, pause, next, previous, and other media controls
   * Respects the capabilities list - only enables commands that are in the list
   */
  private func registerRemoteCommandHandlers() {
    let commandCenter = remoteCommandCenter

    // Play command
    if isCapabilityEnabled("play") {
      commandCenter.playCommand.isEnabled = true
      commandCenter.playCommand.addTarget { [weak self] event in
        self?.handleRemoteCommand(command: "play", data: nil)
        return .success
      }
    } else {
      commandCenter.playCommand.isEnabled = false
    }

    // Pause command
    if isCapabilityEnabled("pause") {
      commandCenter.pauseCommand.isEnabled = true
      commandCenter.pauseCommand.addTarget { [weak self] event in
        self?.handleRemoteCommand(command: "pause", data: nil)
        return .success
      }
    } else {
      commandCenter.pauseCommand.isEnabled = false
    }

    // Stop command
    if isCapabilityEnabled("stop") {
      commandCenter.stopCommand.isEnabled = true
      commandCenter.stopCommand.addTarget { [weak self] event in
        self?.handleRemoteCommand(command: "stop", data: nil)
        return .success
      }
    } else {
      commandCenter.stopCommand.isEnabled = false
    }

    // Next track command
    if isCapabilityEnabled("nextTrack") {
      commandCenter.nextTrackCommand.isEnabled = true
      commandCenter.nextTrackCommand.addTarget { [weak self] event in
        self?.handleRemoteCommand(command: "nextTrack", data: nil)
        return .success
      }
    } else {
      commandCenter.nextTrackCommand.isEnabled = false
    }

    // Previous track command
    if isCapabilityEnabled("previousTrack") {
      commandCenter.previousTrackCommand.isEnabled = true
      commandCenter.previousTrackCommand.addTarget { [weak self] event in
        self?.handleRemoteCommand(command: "previousTrack", data: nil)
        return .success
      }
    } else {
      commandCenter.previousTrackCommand.isEnabled = false
    }

    // Skip forward command
    if isCapabilityEnabled("skipForward") {
      if let skipInterval = getSkipInterval() {
        commandCenter.skipForwardCommand.isEnabled = true
        commandCenter.skipForwardCommand.preferredIntervals = [NSNumber(value: skipInterval)]
        commandCenter.skipForwardCommand.addTarget { [weak self] event in
          var data: [String: Any] = [:]
          if let skipEvent = event as? MPSkipIntervalCommandEvent {
            data["interval"] = skipEvent.interval
          }
          self?.handleRemoteCommand(command: "skipForward", data: data)
          return .success
        }
      }
    } else {
      commandCenter.skipForwardCommand.isEnabled = false
    }

    // Skip backward command
    if isCapabilityEnabled("skipBackward") {
      if let skipInterval = getSkipInterval() {
        commandCenter.skipBackwardCommand.isEnabled = true
        commandCenter.skipBackwardCommand.preferredIntervals = [NSNumber(value: skipInterval)]
        commandCenter.skipBackwardCommand.addTarget { [weak self] event in
          var data: [String: Any] = [:]
          if let skipEvent = event as? MPSkipIntervalCommandEvent {
            data["interval"] = skipEvent.interval
          }
          self?.handleRemoteCommand(command: "skipBackward", data: data)
          return .success
        }
      }
    } else {
      commandCenter.skipBackwardCommand.isEnabled = false
    }

    // Seek command
    if isCapabilityEnabled("seek") {
      commandCenter.changePlaybackPositionCommand.isEnabled = true
      commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
        var data: [String: Any] = [:]
        if let seekEvent = event as? MPChangePlaybackPositionCommandEvent {
          data["position"] = seekEvent.positionTime
        }
        self?.handleRemoteCommand(command: "seek", data: data)
        return .success
      }
    } else {
      commandCenter.changePlaybackPositionCommand.isEnabled = false
    }
    
    print("📱 Remote command handlers registered")
  }
  
  /**
   * Update rating commands based on current rating state
   * Enables/disables like/dislike commands based on whether rating is available
   */
  private func updateRatingCommands() {
    let commandCenter = remoteCommandCenter
    
    // Always drop existing targets first; this runs on every metadata update and
    // re-adding targets would otherwise fire duplicate rating events
    commandCenter.likeCommand.removeTarget(nil)
    commandCenter.dislikeCommand.removeTarget(nil)

    if isRatingEnabled {
      // Enable rating commands
      commandCenter.likeCommand.isEnabled = true
      commandCenter.likeCommand.addTarget { [weak self] event in
        print("📱 iOS: Like command received from remote control")
        self?.handleRemoteCommand(command: "setRating", data: ["rating": true, "type": "heart"])
        return .success
      }
      
      commandCenter.dislikeCommand.isEnabled = true
      commandCenter.dislikeCommand.addTarget { [weak self] event in
        print("📱 iOS: Dislike command received from remote control")
        self?.handleRemoteCommand(command: "setRating", data: ["rating": false, "type": "heart"])
        return .success
      }
      
      print("📱 Rating commands enabled")
    } else {
      // Disable and remove rating command handlers
      commandCenter.likeCommand.isEnabled = false
      commandCenter.dislikeCommand.isEnabled = false
      
      print("📱 Rating commands disabled")
    }
  }

  /**
   * Unregister all remote command handlers
   * Removes all handlers and disables commands
   */
  private func unregisterRemoteCommandHandlers() {
    let commandCenter = remoteCommandCenter
    
    // Remove targets and disable commands
    commandCenter.playCommand.removeTarget(nil)
    commandCenter.playCommand.isEnabled = false
    
    commandCenter.pauseCommand.removeTarget(nil)
    commandCenter.pauseCommand.isEnabled = false
    
    commandCenter.stopCommand.removeTarget(nil)
    commandCenter.stopCommand.isEnabled = false
    
    commandCenter.nextTrackCommand.removeTarget(nil)
    commandCenter.nextTrackCommand.isEnabled = false
    
    commandCenter.previousTrackCommand.removeTarget(nil)
    commandCenter.previousTrackCommand.isEnabled = false
    
    commandCenter.skipForwardCommand.removeTarget(nil)
    commandCenter.skipForwardCommand.isEnabled = false
    
    commandCenter.skipBackwardCommand.removeTarget(nil)
    commandCenter.skipBackwardCommand.isEnabled = false
    
    commandCenter.changePlaybackPositionCommand.removeTarget(nil)
    commandCenter.changePlaybackPositionCommand.isEnabled = false
    
    commandCenter.likeCommand.removeTarget(nil)
    commandCenter.likeCommand.isEnabled = false
    
    commandCenter.dislikeCommand.removeTarget(nil)
    commandCenter.dislikeCommand.isEnabled = false
    
    print("📱 Remote command handlers unregistered")
  }

  /**
   * Handle remote command events
   * Processes remote control commands and sends events to JavaScript
   */
  private func handleRemoteCommand(command: String, data: [String: Any]?) {
    print("📱 iOS: handleRemoteCommand called with command: \(command)")
    
    let eventData: [String: Any] = [
      "command": command,
      "data": data ?? [:],
      "timestamp": Date().timeIntervalSince1970 * 1000 // Convert to milliseconds
    ]
    
    print("📱 iOS: Preparing to send event: \(eventData)")
    
    // Send event to JavaScript using proper Expo modules API
    DispatchQueue.main.async { [weak self] in
      self?.sendEvent("mediaControlEvent", eventData)
      print("📱 iOS: Event sent successfully: \(command)")
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
  private func getSkipInterval() -> Double? {
    if let iosConfig = controlOptions["ios"] as? [String: Any],
       let skipInterval = iosConfig["skipInterval"] as? Double {
      return skipInterval
    }
    return 15.0 // Default 15 seconds
  }

  /**
   * Load artwork from URI
   * Handles both local and remote artwork loading with proper error handling
   */
  private func loadArtwork(uri: String, completion: @escaping (MPMediaItemArtwork?) -> Void) async {
    if uri.hasPrefix("http://") || uri.hasPrefix("https://") {
      // Load remote image
      await loadRemoteArtwork(uri: uri, completion: completion)
    } else {
      // Load local image
      loadLocalArtwork(uri: uri, completion: completion)
    }
  }

  /**
   * Load artwork from remote URL
   * Downloads and caches remote artwork images
   */
  private func loadRemoteArtwork(uri: String, completion: @escaping (MPMediaItemArtwork?) -> Void) async {
    guard let url = URL(string: uri) else {
      completion(nil)
      return
    }
    
    do {
      let (data, _) = try await URLSession.shared.data(from: url)
      
      if let image = UIImage(data: data) {
        let artwork = MPMediaItemArtwork(boundsSize: image.size) { size in
          return image
        }
        completion(artwork)
      } else {
        completion(nil)
      }
    } catch {
      print("❌ Failed to load remote artwork: \(error)")
      completion(nil)
    }
  }

  /**
   * Load artwork from local file
   * Loads artwork from local file system or app bundle
   */
  private func loadLocalArtwork(uri: String, completion: @escaping (MPMediaItemArtwork?) -> Void) {
    var imagePath = uri
    
    // Remove file:// prefix if present
    if imagePath.hasPrefix("file://") {
      imagePath = String(imagePath.dropFirst(7))
    }
    
    var image: UIImage?
    
    // Try to load from file system
    if FileManager.default.fileExists(atPath: imagePath) {
      image = UIImage(contentsOfFile: imagePath)
    } else {
      // Try to load from app bundle
      image = UIImage(named: imagePath)
    }
    
    if let image = image {
      let artwork = MPMediaItemArtwork(boundsSize: image.size) { size in
        return image
      }
      completion(artwork)
    } else {
      print("❌ Failed to load local artwork: \(imagePath)")
      completion(nil)
    }
  }
}
