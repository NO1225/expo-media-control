import { NativeModule, requireNativeModule } from 'expo';

// =============================================
// CUSTOM ERROR TYPES
// =============================================

/**
 * Base error class for media control errors
 */
export class MediaControlError extends Error {
  constructor(message: string, public readonly code?: string, public readonly cause?: Error) {
    super(message);
    this.name = 'MediaControlError';
  }
}

/**
 * Error thrown when validation fails
 */
export class ValidationError extends MediaControlError {
  constructor(message: string, public readonly field?: string) {
    super(message, 'VALIDATION_ERROR');
    this.name = 'ValidationError';
  }
}

/**
 * Error thrown when native module operations fail
 */
export class NativeError extends MediaControlError {
  constructor(message: string, code?: string, cause?: Error) {
    super(message, code, cause);
    this.name = 'NativeError';
  }
}

/**
 * Error thrown when media controls are not enabled
 */
export class NotEnabledError extends MediaControlError {
  constructor(message: string = 'Media controls are not enabled') {
    super(message, 'NOT_ENABLED');
    this.name = 'NotEnabledError';
  }
}

// =============================================
// VALIDATION UTILITIES
// =============================================

/**
 * Validates media metadata input
 */
function validateMetadata(metadata: any): asserts metadata is MediaMetadata {
  if (!metadata || typeof metadata !== 'object') {
    throw new ValidationError('Metadata must be an object', 'metadata');
  }

  // Validate optional string fields
  const stringFields = ['title', 'artist', 'album', 'genre', 'date'];
  for (const field of stringFields) {
    if (metadata[field] !== undefined && typeof metadata[field] !== 'string') {
      throw new ValidationError(`${field} must be a string`, field);
    }
  }

  // Validate optional number fields
  const numberFields = ['duration', 'elapsedTime', 'trackNumber', 'albumTrackCount'];
  for (const field of numberFields) {
    if (metadata[field] !== undefined && typeof metadata[field] !== 'number') {
      throw new ValidationError(`${field} must be a number`, field);
    }
    if (metadata[field] !== undefined && metadata[field] < 0) {
      throw new ValidationError(`${field} must be non-negative`, field);
    }
  }

  // Validate artwork
  if (metadata.artwork !== undefined) {
    if (!metadata.artwork || typeof metadata.artwork !== 'object') {
      throw new ValidationError('artwork must be an object', 'artwork');
    }
    if (typeof metadata.artwork.uri !== 'string' || metadata.artwork.uri.length === 0) {
      throw new ValidationError('artwork.uri must be a non-empty string', 'artwork.uri');
    }
    if (metadata.artwork.width !== undefined && typeof metadata.artwork.width !== 'number') {
      throw new ValidationError('artwork.width must be a number', 'artwork.width');
    }
    if (metadata.artwork.height !== undefined && typeof metadata.artwork.height !== 'number') {
      throw new ValidationError('artwork.height must be a number', 'artwork.height');
    }
  }

  // Validate rating
  if (metadata.rating !== undefined) {
    if (!metadata.rating || typeof metadata.rating !== 'object') {
      throw new ValidationError('rating must be an object', 'rating');
    }
    if (!Object.values(RatingType).includes(metadata.rating.type)) {
      throw new ValidationError('rating.type must be a valid RatingType', 'rating.type');
    }
    if (typeof metadata.rating.value !== 'boolean' && typeof metadata.rating.value !== 'number') {
      throw new ValidationError('rating.value must be a boolean or number', 'rating.value');
    }
  }
}

/**
 * Validates playback state input
 */
function validatePlaybackState(state: any): asserts state is PlaybackState {
  if (typeof state !== 'number') {
    throw new ValidationError('Playback state must be a number', 'state');
  }
  if (!Object.values(PlaybackState).includes(state)) {
    throw new ValidationError('Invalid playback state value', 'state');
  }
}

/**
 * Validates position input
 */
function validatePosition(position: any): asserts position is number {
  if (typeof position !== 'number') {
    throw new ValidationError('Position must be a number', 'position');
  }
  if (position < 0) {
    throw new ValidationError('Position must be non-negative', 'position');
  }
  if (!isFinite(position)) {
    throw new ValidationError('Position must be finite', 'position');
  }
}

/**
 * Validates media control options
 */
function validateMediaControlOptions(options: any): asserts options is MediaControlOptions {
  if (!options || typeof options !== 'object') {
    throw new ValidationError('Options must be an object', 'options');
  }

  if (options.capabilities !== undefined) {
    if (!Array.isArray(options.capabilities)) {
      throw new ValidationError('capabilities must be an array', 'capabilities');
    }
    for (const capability of options.capabilities) {
      if (!Object.values(Command).includes(capability)) {
        throw new ValidationError(`Invalid capability: ${capability}`, 'capabilities');
      }
    }
  }

  if (options.notification !== undefined) {
    if (typeof options.notification !== 'object') {
      throw new ValidationError('notification must be an object', 'notification');
    }
  }

  if (options.ios !== undefined) {
    if (typeof options.ios !== 'object') {
      throw new ValidationError('ios must be an object', 'ios');
    }
  }

  if (options.android !== undefined) {
    if (typeof options.android !== 'object') {
      throw new ValidationError('android must be an object', 'android');
    }
  }
}

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
 * 
 * Note: Audio focus management should be handled by your media player,
 * not by this control module.
 */
export interface MediaControlOptions {
  capabilities?: Command[];
  notification?: {
    icon?: string;
    largeIcon?: MediaArtwork;
    color?: string;
    showWhenClosed?: boolean;
  };
  ios?: {
    skipInterval?: number;
  };
  android?: {
    skipInterval?: number;
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

console.log('📱 JS: Native module loaded:', nativeModule);

/**
 * Map to store event listeners for manual management
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
      // Validate input
      if (options !== undefined) {
        validateMediaControlOptions(options);
      }

      await nativeModule.enableMediaControls(options);

      // Add native event listeners
      (nativeModule as any).addListener('mediaControlEvent', this._dispatchMediaControlEvent);
      (nativeModule as any).addListener('audioInterruptionEvent', this._dispatchAudioInterruptionEvent);
      (nativeModule as any).addListener('volumeChangeEvent', this._dispatchVolumeChangeEvent);
    } catch (error) {
      if (error instanceof ValidationError) {
        throw error;
      }
      const errorMessage = error instanceof Error ? error.message : String(error);
      const nativeError = new NativeError(
        `Failed to enable media controls: ${errorMessage}`,
        'ENABLE_FAILED',
        error instanceof Error ? error : undefined
      );
      console.error(nativeError.message);
      throw nativeError;
    }
  };

  /**
   * Disable media controls and clean up all resources
   * Stops the media session and removes all handlers
   */
  disableMediaControls = async (): Promise<void> => {
    try {
      await nativeModule.disableMediaControls();

      // Remove all native event listeners
      (nativeModule as any).removeAllListeners('mediaControlEvent');
      (nativeModule as any).removeAllListeners('audioInterruptionEvent');
      (nativeModule as any).removeAllListeners('volumeChangeEvent');
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
      // Validate input
      validateMetadata(metadata);

      // Filter out undefined values to prevent native conversion errors
      // This ensures robust handling of optional metadata fields
      const cleanMetadata = Object.fromEntries(
        Object.entries(metadata).filter(([_, value]) => value !== undefined)
      ) as MediaMetadata;

      console.log('📱 JS: Sending cleaned metadata to native:', JSON.stringify(cleanMetadata, null, 2));

      await nativeModule.updateMetadata(cleanMetadata);
    } catch (error) {
      if (error instanceof ValidationError) {
        throw error;
      }
      const errorMessage = error instanceof Error ? error.message : String(error);
      const nativeError = new NativeError(
        `Failed to update metadata: ${errorMessage}`,
        'UPDATE_METADATA_FAILED',
        error instanceof Error ? error : undefined
      );
      console.error(nativeError.message);
      throw nativeError;
    }
  };

  /**
   * Update the current playback state and position
   * Updates the system about current playback status
   */
  updatePlaybackState = async (state: PlaybackState, position?: number): Promise<void> => {
    try {
      // Validate input
      validatePlaybackState(state);
      if (position !== undefined) {
        validatePosition(position);
      }

      await nativeModule.updatePlaybackState(state, position);
    } catch (error) {
      if (error instanceof ValidationError) {
        throw error;
      }
      const errorMessage = error instanceof Error ? error.message : String(error);
      const nativeError = new NativeError(
        `Failed to update playback state: ${errorMessage}`,
        'UPDATE_STATE_FAILED',
        error instanceof Error ? error : undefined
      );
      console.error(nativeError.message);
      throw nativeError;
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
    console.log('📱 JS: Adding media control event listener');
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
            console.log('📱 JS: Dispatching media control event:', event);

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
