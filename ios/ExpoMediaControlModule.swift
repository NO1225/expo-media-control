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
  
  /// Whether media controls are currently enabled
  private var isControlsEnabled: Bool = false
  
  /// Configuration options for the media controls
  private var controlOptions: [String: Any] = [:]
  
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
     */
    AsyncFunction("updatePlaybackState") { (state: Int, position: Double?) in
      return try await self.updatePlaybackState(state: state, position: position)
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
    Events("mediaControlEvent", "audioInterruption", "volumeChange")
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
    // Store configuration options
    if let opts = options {
      controlOptions = opts
    }
    
    // Configure audio session for playback
    try await configureAudioSession()
    
    // Register remote command handlers
    registerRemoteCommandHandlers()
    
    // Mark controls as enabled
    isControlsEnabled = true
    
    print("📱 Media controls enabled successfully")
  }

  /**
   * Disable media controls implementation
   * Cleans up all handlers and resets state
   */
  private func disableMediaControls() async throws {
    // Unregister all remote command handlers
    unregisterRemoteCommandHandlers()
    
    // Clear now playing info
    nowPlayingInfoCenter.nowPlayingInfo = nil
    
    // Reset state
    isControlsEnabled = false
    currentMetadata.removeAll()
    currentPlaybackState = 0 // PlaybackState.NONE
    currentPosition = 0.0
    controlOptions.removeAll()
    
    print("📱 Media controls disabled successfully")
  }

  /**
   * Update metadata implementation
   * Converts metadata dictionary and updates MPNowPlayingInfoCenter
   */
  private func updateMetadata(metadata: [String: Any]) async throws {
    currentMetadata = metadata
    
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
    
    if let genre = metadata["genre"] as? String {
      nowPlayingInfo[MPMediaItemPropertyGenre] = genre
    }
    
    if let trackNumber = metadata["trackNumber"] as? Int {
      nowPlayingInfo[MPMediaItemPropertyAlbumTrackNumber] = trackNumber
    }
    
    if let albumTrackCount = metadata["albumTrackCount"] as? Int {
      nowPlayingInfo[MPMediaItemPropertyAlbumTrackCount] = albumTrackCount
    }
    
    // Set elapsed time
    nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentPosition
    
    // Set playback rate based on current state
    let playbackRate: Double = currentPlaybackState == 2 ? 1.0 : 0.0 // 2 = PlaybackState.PLAYING
    nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = playbackRate
    
    // Handle artwork asynchronously
    if let artworkDict = metadata["artwork"] as? [String: Any],
       let uri = artworkDict["uri"] as? String {
      await loadArtwork(uri: uri) { [weak self] artwork in
        if let artwork = artwork {
          nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork
        }
        
        // Update now playing info center
        DispatchQueue.main.async {
          self?.nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
        }
      }
    } else {
      // Update without artwork
      DispatchQueue.main.async { [weak self] in
        self?.nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
      }
    }
    
    print("📱 Metadata updated: \(metadata["title"] ?? "Unknown") - \(metadata["artist"] ?? "Unknown")")
  }

  /**
   * Update playback state implementation
   * Updates the system about current playback status
   */
  private func updatePlaybackState(state: Int, position: Double?) async throws {
    currentPlaybackState = state
    
    if let pos = position {
      currentPosition = pos
    }
    
    // Update now playing info with new playback information
    var nowPlayingInfo = nowPlayingInfoCenter.nowPlayingInfo ?? [:]
    
    // Update elapsed time
    nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentPosition
    
    // Update playback rate based on state
    let playbackRate: Double
    switch state {
    case 2: // PlaybackState.PLAYING
      playbackRate = 1.0
    case 3: // PlaybackState.PAUSED
      playbackRate = 0.0
    case 4: // PlaybackState.BUFFERING
      playbackRate = 0.0
    default: // NONE, STOPPED, ERROR
      playbackRate = 0.0
    }
    nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = playbackRate
    
    // Update the system
    DispatchQueue.main.async { [weak self] in
      self?.nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
    }
    
    print("📱 Playback state updated: \(state), position: \(currentPosition)")
  }

  /**
   * Reset controls implementation
   * Clears all information and returns to initial state
   */
  private func resetControls() async throws {
    currentMetadata.removeAll()
    currentPlaybackState = 0 // PlaybackState.NONE
    currentPosition = 0.0
    
    // Clear now playing info
    DispatchQueue.main.async { [weak self] in
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
   */
  private func configureAudioSession() async throws {
    do {
      // Set audio session category for playback
      try audioSession.setCategory(.playback, mode: .default, options: [.allowBluetooth, .allowBluetoothA2DP])
      
      // Activate the audio session
      try audioSession.setActive(true)
      
      print("📱 Audio session configured for playback")
    } catch {
      print("❌ Failed to configure audio session: \(error)")
      throw error
    }
  }

  // =============================================
  // REMOTE COMMAND HANDLING
  // Methods for registering and handling remote control commands
  // =============================================

  /**
   * Register all remote command handlers
   * Sets up handlers for play, pause, next, previous, and other media controls
   */
  private func registerRemoteCommandHandlers() {
    let commandCenter = remoteCommandCenter
    
    // Play command
    commandCenter.playCommand.isEnabled = true
    commandCenter.playCommand.addTarget { [weak self] event in
      self?.handleRemoteCommand(command: "play", data: nil)
      return .success
    }
    
    // Pause command
    commandCenter.pauseCommand.isEnabled = true
    commandCenter.pauseCommand.addTarget { [weak self] event in
      self?.handleRemoteCommand(command: "pause", data: nil)
      return .success
    }
    
    // Stop command
    commandCenter.stopCommand.isEnabled = true
    commandCenter.stopCommand.addTarget { [weak self] event in
      self?.handleRemoteCommand(command: "stop", data: nil)
      return .success
    }
    
    // Next track command
    commandCenter.nextTrackCommand.isEnabled = true
    commandCenter.nextTrackCommand.addTarget { [weak self] event in
      self?.handleRemoteCommand(command: "nextTrack", data: nil)
      return .success
    }
    
    // Previous track command
    commandCenter.previousTrackCommand.isEnabled = true
    commandCenter.previousTrackCommand.addTarget { [weak self] event in
      self?.handleRemoteCommand(command: "previousTrack", data: nil)
      return .success
    }
    
    // Skip forward command
    if let skipInterval = getSkipInterval() {
      commandCenter.skipForwardCommand.isEnabled = true
      commandCenter.skipForwardCommand.preferredIntervals = [NSNumber(value: skipInterval)]
      commandCenter.skipForwardCommand.addTarget { [weak self] event in
        if let skipEvent = event as? MPSkipIntervalCommandEvent {
          self?.handleRemoteCommand(command: "skipForward", data: ["interval": skipEvent.interval])
        }
        return .success
      }
    }
    
    // Skip backward command
    if let skipInterval = getSkipInterval() {
      commandCenter.skipBackwardCommand.isEnabled = true
      commandCenter.skipBackwardCommand.preferredIntervals = [NSNumber(value: skipInterval)]
      commandCenter.skipBackwardCommand.addTarget { [weak self] event in
        if let skipEvent = event as? MPSkipIntervalCommandEvent {
          self?.handleRemoteCommand(command: "skipBackward", data: ["interval": skipEvent.interval])
        }
        return .success
      }
    }
    
    // Seek command
    commandCenter.changePlaybackPositionCommand.isEnabled = true
    commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
      if let seekEvent = event as? MPChangePlaybackPositionCommandEvent {
        self?.handleRemoteCommand(command: "seek", data: ["position": seekEvent.positionTime])
      }
      return .success
    }
    
    // Rating commands (like/dislike)
    commandCenter.likeCommand.isEnabled = true
    commandCenter.likeCommand.addTarget { [weak self] event in
      self?.handleRemoteCommand(command: "setRating", data: ["rating": true, "type": "heart"])
      return .success
    }
    
    commandCenter.dislikeCommand.isEnabled = true
    commandCenter.dislikeCommand.addTarget { [weak self] event in
      self?.handleRemoteCommand(command: "setRating", data: ["rating": false, "type": "heart"])
      return .success
    }
    
    print("📱 Remote command handlers registered")
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
    let event: [String: Any] = [
      "command": command,
      "data": data as Any,
      "timestamp": Date().timeIntervalSince1970 * 1000 // Convert to milliseconds
    ]
    
    // Send event to JavaScript
    sendEvent("mediaControlEvent", event)
    
    print("📱 Remote command handled: \(command)")
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
