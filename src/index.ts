// Re-export types and enums from ExpoMediaControlModule
export {
  PlaybackState,
  Command,
  RatingType,
  MediaArtwork,
  MediaRating,
  MediaMetadata,
  MediaControlOptions,
  MediaControlEvent,
  VolumeChange,
  MediaControlEventListener,
  VolumeChangeListener,
  // Error types
  MediaControlError,
  ValidationError,
  NativeError,
  NotEnabledError,
} from './ExpoMediaControlModule';

import ExpoMediaControlModule from './ExpoMediaControlModule';

// =============================================
// MAIN API INTERFACE
// =============================================

/**
 * Main interface for the Expo Media Control module
 * Provides methods to control media playback and handle remote control events
 */
export interface ExpoMediaControlInterface {
  // Media control methods
  enableMediaControls: typeof ExpoMediaControlModule.enableMediaControls;
  disableMediaControls: typeof ExpoMediaControlModule.disableMediaControls;
  updateMetadata: typeof ExpoMediaControlModule.updateMetadata;
  updatePlaybackState: typeof ExpoMediaControlModule.updatePlaybackState;
  resetControls: typeof ExpoMediaControlModule.resetControls;
  
  // Event handling methods
  addListener: typeof ExpoMediaControlModule.addListener;
  addVolumeChangeListener: typeof ExpoMediaControlModule.addVolumeChangeListener;
  removeAllListeners: typeof ExpoMediaControlModule.removeAllListeners;
  
  // Utility methods
  isEnabled: typeof ExpoMediaControlModule.isEnabled;
  getCurrentMetadata: typeof ExpoMediaControlModule.getCurrentMetadata;
  getCurrentState: typeof ExpoMediaControlModule.getCurrentState;
}

// =============================================
// EXPORTED API
// =============================================

/**
 * Main API object for media controls
 * Implements the ExpoMediaControlInterface for managing media playback controls
 */
export const MediaControl: ExpoMediaControlInterface = {
  enableMediaControls: ExpoMediaControlModule.enableMediaControls,
  disableMediaControls: ExpoMediaControlModule.disableMediaControls,
  updateMetadata: ExpoMediaControlModule.updateMetadata,
  updatePlaybackState: ExpoMediaControlModule.updatePlaybackState,
  resetControls: ExpoMediaControlModule.resetControls,
  addListener: ExpoMediaControlModule.addListener,
  addVolumeChangeListener: ExpoMediaControlModule.addVolumeChangeListener,
  removeAllListeners: ExpoMediaControlModule.removeAllListeners,
  isEnabled: ExpoMediaControlModule.isEnabled,
  getCurrentMetadata: ExpoMediaControlModule.getCurrentMetadata,
  getCurrentState: ExpoMediaControlModule.getCurrentState,
};

// Export individual functions for backward compatibility
export const {
  enableMediaControls,
  disableMediaControls,
  updateMetadata,
  updatePlaybackState,
  resetControls,
  addListener,
  addVolumeChangeListener,
  removeAllListeners,
  isEnabled,
  getCurrentMetadata,
  getCurrentState,
} = MediaControl;

// Export everything for convenience
export default MediaControl;
