import { NativeModule, requireNativeModule } from 'expo';

// =============================================
// TYPE DEFINITIONS
// =============================================

/**
 * Represents the current state of media playback
 */
export enum PlaybackState {
  NONE = 0,
  STOPPED = 1,
  PLAYING = 2,
  PAUSED = 3,
  BUFFERING = 4,
  ERROR = 5,
}

/**
 * Represents different types of media control commands
 */
export enum Command {
  PLAY = 'play',
  PAUSE = 'pause',
  STOP = 'stop',
  NEXT_TRACK = 'nextTrack',
  PREVIOUS_TRACK = 'previousTrack',
  SKIP_FORWARD = 'skipForward',
  SKIP_BACKWARD = 'skipBackward',
  SEEK = 'seek',
  SET_RATING = 'setRating',
  VOLUME_UP = 'volumeUp',
  VOLUME_DOWN = 'volumeDown',
}

/**
 * Rating types for media content
 */
export enum RatingType {
  HEART = 'heart',
  THUMBS_UP_DOWN = 'thumbsUpDown',
  THREE_STARS = 'threeStars',
  FOUR_STARS = 'fourStars',
  FIVE_STARS = 'fiveStars',
  PERCENTAGE = 'percentage',
}

/**
 * Represents artwork/album cover information
 */
export interface MediaArtwork {
  uri: string;
  width?: number;
  height?: number;
}

/**
 * Represents rating information for media content
 */
export interface MediaRating {
  type: RatingType;
  value: boolean | number;
  maxValue?: number;
}

/**
 * Complete media metadata information
 */
export interface MediaMetadata {
  title?: string;
  artist?: string;
  album?: string;
  artwork?: MediaArtwork;
  duration?: number;
  elapsedTime?: number;
  genre?: string;
  trackNumber?: number;
  albumTrackCount?: number;
  date?: string;
  rating?: MediaRating;
  color?: string;
  colorized?: boolean;
}

/**
 * Configuration options for media controls
 */
export interface MediaControlOptions {
  capabilities?: Command[];
  notification?: {
    icon?: string;
    largeIcon?: MediaArtwork;
    color?: string;
    showWhenClosed?: boolean;
    skipInterval?: number;
  };
  ios?: {
    skipInterval?: number;
    showPlaybackPosition?: boolean;
  };
  android?: {
    requestAudioFocus?: boolean;
    stopForegroundGracePeriod?: number;
  };
}

/**
 * Media control event data
 */
export interface MediaControlEvent {
  command: Command;
  data?: any;
  timestamp: number;
}

/**
 * Audio interruption information
 */
export interface AudioInterruption {
  type: 'begin' | 'end';
  category?: string;
  shouldResume?: boolean;
}

/**
 * Volume change information
 */
export interface VolumeChange {
  volume: number;
  userInitiated: boolean;
}

// Event listener types
export type MediaControlEventListener = (event: MediaControlEvent) => void;
export type AudioInterruptionListener = (interruption: AudioInterruption) => void;
export type VolumeChangeListener = (change: VolumeChange) => void;

// =============================================
// NATIVE MODULE INTERFACE
// =============================================

/**
 * Native module interface for Expo Media Control
 * This interface defines all the methods that the native iOS and Android modules must implement
 */
declare class ExpoMediaControlNativeModule extends NativeModule {
  /**
   * Enable media controls with specified configuration
   * Initializes the media session and sets up remote control handlers
   */
  enableMediaControls(options?: MediaControlOptions): Promise<void>;

  /**
   * Disable media controls and clean up all resources
   * Stops the media session and removes all handlers
   */
  disableMediaControls(): Promise<void>;

  /**
   * Update the media metadata displayed in system controls
   * Updates notification, lock screen, and control center information
   */
  updateMetadata(metadata: MediaMetadata): Promise<void>;

  /**
   * Update the current playback state and position
   * Updates the system about current playback status
   */
  updatePlaybackState(state: PlaybackState, position?: number): Promise<void>;

  /**
   * Reset all media control information to default state
   * Clears all metadata and resets playback state
   */
  resetControls(): Promise<void>;

  /**
   * Check if media controls are currently enabled
   * Returns whether the media session is active
   */
  isEnabled(): Promise<boolean>;

  /**
   * Get the current media metadata
   * Returns the currently set metadata information
   */
  getCurrentMetadata(): Promise<MediaMetadata | null>;

  /**
   * Get the current playback state
   * Returns the current playback status
   */
  getCurrentState(): Promise<PlaybackState>;
}

// =============================================
// MODULE IMPLEMENTATION
// =============================================

// Create the native module instance
const nativeModule = requireNativeModule<ExpoMediaControlNativeModule>('ExpoMediaControl');

/**
 * Map to store event listeners
 * This allows us to manage subscriptions manually since Expo's EventEmitter
 * has strict type checking that may not work with our custom events
 */
const eventListeners: {
  mediaControl: MediaControlEventListener[];
  audioInterruption: AudioInterruptionListener[];
  volumeChange: VolumeChangeListener[];
} = {
  mediaControl: [],
  audioInterruption: [],
  volumeChange: [],
};

/**
 * Extended module class that combines native methods with simplified event handling
 * This provides a complete interface for media control functionality
 */
class ExtendedExpoMediaControlModule {
  // =============================================
  // NATIVE METHOD PROXIES
  // Forward calls to the native module with proper error handling
  // =============================================
  
  /**
   * Enable media controls with specified configuration
   * Initializes the media session and sets up remote control handlers
   */
  enableMediaControls = async (options?: MediaControlOptions): Promise<void> => {
    try {
      await nativeModule.enableMediaControls(options);
    } catch (error) {
      console.error('Failed to enable media controls:', error);
      throw error;
    }
  };

  /**
   * Disable media controls and clean up all resources
   * Stops the media session and removes all handlers
   */
  disableMediaControls = async (): Promise<void> => {
    try {
      await nativeModule.disableMediaControls();
    } catch (error) {
      console.error('Failed to disable media controls:', error);
      throw error;
    }
  };

  /**
   * Update the media metadata displayed in system controls
   * Updates notification, lock screen, and control center information
   */
  updateMetadata = async (metadata: MediaMetadata): Promise<void> => {
    try {
      await nativeModule.updateMetadata(metadata);
    } catch (error) {
      console.error('Failed to update metadata:', error);
      throw error;
    }
  };

  /**
   * Update the current playback state and position
   * Updates the system about current playback status
   */
  updatePlaybackState = async (state: PlaybackState, position?: number): Promise<void> => {
    try {
      await nativeModule.updatePlaybackState(state, position);
    } catch (error) {
      console.error('Failed to update playback state:', error);
      throw error;
    }
  };

  /**
   * Reset all media control information to default state
   * Clears all metadata and resets playback state
   */
  resetControls = async (): Promise<void> => {
    try {
      await nativeModule.resetControls();
    } catch (error) {
      console.error('Failed to reset controls:', error);
      throw error;
    }
  };

  /**
   * Check if media controls are currently enabled
   * Returns whether the media session is active
   */
  isEnabled = async (): Promise<boolean> => {
    try {
      return await nativeModule.isEnabled();
    } catch (error) {
      console.error('Failed to check if controls are enabled:', error);
      return false;
    }
  };

  /**
   * Get the current media metadata
   * Returns the currently set metadata information
   */
  getCurrentMetadata = async (): Promise<MediaMetadata | null> => {
    try {
      return await nativeModule.getCurrentMetadata();
    } catch (error) {
      console.error('Failed to get current metadata:', error);
      return null;
    }
  };

  /**
   * Get the current playback state
   * Returns the current playback status
   */
  getCurrentState = async (): Promise<PlaybackState> => {
    try {
      return await nativeModule.getCurrentState();
    } catch (error) {
      console.error('Failed to get current state:', error);
      return PlaybackState.NONE;
    }
  };

  // =============================================
  // SIMPLIFIED EVENT HANDLING METHODS
  // Use manual listener management for better control
  // =============================================

  /**
   * Add listener for media control events (play, pause, next, etc.)
   * These events are triggered when users interact with system media controls
   * @param listener Function to call when media control events occur
   * @returns Function to remove the listener
   */
  addListener = (listener: MediaControlEventListener): (() => void) => {
    eventListeners.mediaControl.push(listener);
    
    // Return removal function
    return () => {
      const index = eventListeners.mediaControl.indexOf(listener);
      if (index > -1) {
        eventListeners.mediaControl.splice(index, 1);
      }
    };
  };

  /**
   * Add listener for audio interruption events (calls, notifications)
   * These events help manage audio focus and playback interruptions
   * @param listener Function to call when audio interruptions occur
   * @returns Function to remove the listener
   */
  addAudioInterruptionListener = (listener: AudioInterruptionListener): (() => void) => {
    eventListeners.audioInterruption.push(listener);
    
    // Return removal function
    return () => {
      const index = eventListeners.audioInterruption.indexOf(listener);
      if (index > -1) {
        eventListeners.audioInterruption.splice(index, 1);
      }
    };
  };

  /**
   * Add listener for volume change events
   * These events are triggered when system volume changes
   * @param listener Function to call when volume changes
   * @returns Function to remove the listener
   */
  addVolumeChangeListener = (listener: VolumeChangeListener): (() => void) => {
    eventListeners.volumeChange.push(listener);
    
    // Return removal function
    return () => {
      const index = eventListeners.volumeChange.indexOf(listener);
      if (index > -1) {
        eventListeners.volumeChange.splice(index, 1);
      }
    };
  };

  /**
   * Remove all event listeners for all event types
   * Cleans up all subscribed event handlers
   * @returns Promise that resolves when all listeners are removed
   */
  removeAllListeners = async (): Promise<void> => {
    eventListeners.mediaControl.length = 0;
    eventListeners.audioInterruption.length = 0;
    eventListeners.volumeChange.length = 0;
  };

  // =============================================
  // INTERNAL EVENT DISPATCH METHODS
  // These will be called by the native modules
  // =============================================

  /**
   * Internal method to dispatch media control events
   * This will be called by the native modules when control events occur
   */
  _dispatchMediaControlEvent = (event: MediaControlEvent): void => {
    eventListeners.mediaControl.forEach(listener => {
      try {
        listener(event);
      } catch (error) {
        console.error('Error in media control event listener:', error);
      }
    });
  };

  /**
   * Internal method to dispatch audio interruption events
   * This will be called by the native modules when interruptions occur
   */
  _dispatchAudioInterruptionEvent = (interruption: AudioInterruption): void => {
    eventListeners.audioInterruption.forEach(listener => {
      try {
        listener(interruption);
      } catch (error) {
        console.error('Error in audio interruption event listener:', error);
      }
    });
  };

  /**
   * Internal method to dispatch volume change events
   * This will be called by the native modules when volume changes
   */
  _dispatchVolumeChangeEvent = (change: VolumeChange): void => {
    eventListeners.volumeChange.forEach(listener => {
      try {
        listener(change);
      } catch (error) {
        console.error('Error in volume change event listener:', error);
      }
    });
  };
}

// Export the extended module instance
export default new ExtendedExpoMediaControlModule();
