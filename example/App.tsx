import React, { useState, useEffect } from 'react';
import {
  Button,
  SafeAreaView,
  ScrollView,
  Text,
  View,
  StyleSheet,
  Alert,
  TextInput,
  Switch,
  Image,
} from 'react-native';
import {
  MediaControl,
  PlaybackState,
  Command,
  RatingType,
  MediaControlEvent,
  AudioInterruption,
  VolumeChange,
} from 'expo-media-control';

/**
 * Comprehensive Example App for Expo Media Control
 * 
 * This app demonstrates all the features of the media control module including:
 * - Enabling/disabling media controls
 * - Updating metadata with artwork
 * - Managing playback state
 * - Handling remote control events
 * - Audio interruption handling
 * - Volume change monitoring
 */
export default function App() {
  // =============================================
  // STATE MANAGEMENT
  // =============================================
  
  const [isEnabled, setIsEnabled] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentPosition, setCurrentPosition] = useState(0);
  const [volume, setVolume] = useState(1.0);
  const [lastEvent, setLastEvent] = useState<string>('No events yet');
  
  // Track metadata state
  const [trackTitle, setTrackTitle] = useState('Sample Track');
  const [trackArtist, setTrackArtist] = useState('Sample Artist');
  const [trackAlbum, setTrackAlbum] = useState('Sample Album');
  const [trackDuration, setTrackDuration] = useState(180); // 3 minutes
  const [showArtwork, setShowArtwork] = useState(true);
  
  // Sample tracks for demonstration
  const sampleTracks = [
    {
      title: 'Test Artwork Track',
      artist: 'RadDy Questions',
      album: 'Logo Test Album',
      duration: 180, // 3:00
      artwork: 'https://raddyquestions.com/assets/img/logo/logo.png'
    },
    {
      title: 'Hotel California',
      artist: 'Eagles',
      album: 'Hotel California',
      duration: 391, // 6:31
      artwork: 'https://upload.wikimedia.org/wikipedia/en/4/49/Hotelcalifornia.jpg'
    },
    {
      title: 'Stairway to Heaven',
      artist: 'Led Zeppelin',
      album: 'Led Zeppelin IV',
      duration: 482, // 8:02
      artwork: 'https://upload.wikimedia.org/wikipedia/en/2/26/Led_Zeppelin_-_Led_Zeppelin_IV.jpg'
    }
  ];
  const [currentTrackIndex, setCurrentTrackIndex] = useState(0);

  // =============================================
  // EVENT HANDLERS SETUP
  // =============================================
  
  useEffect(() => {
    // Set up media control event listener
    const removeMediaListener = MediaControl.addListener((event: MediaControlEvent) => {
      console.log('📱 Media Control Event:', event);
      setLastEvent(`Media: ${event.command} at ${new Date(event.timestamp).toLocaleTimeString()}`);
      
      // Handle different commands
      switch (event.command) {
        case Command.PLAY:
          handlePlay();
          break;
        case Command.PAUSE:
          handlePause();
          break;
        case Command.STOP:
          handleStop();
          break;
        case Command.NEXT_TRACK:
          handleNextTrack();
          break;
        case Command.PREVIOUS_TRACK:
          handlePreviousTrack();
          break;
        case Command.SKIP_FORWARD:
          handleSkipForward(event.data?.interval || 15);
          break;
        case Command.SKIP_BACKWARD:
          handleSkipBackward(event.data?.interval || 15);
          break;
        case Command.SEEK:
          if (event.data?.position !== undefined) {
            handleSeek(event.data.position);
          }
          break;
        case Command.SET_RATING:
          handleRating(event.data);
          break;
      }
    });

    // Set up audio interruption listener
    const removeInterruptionListener = MediaControl.addAudioInterruptionListener((interruption: AudioInterruption) => {
      console.log('🔊 Audio Interruption:', interruption);
      setLastEvent(`Interruption: ${interruption.type} at ${new Date().toLocaleTimeString()}`);
      
      if (interruption.type === 'begin') {
        // Pause playback on interruption
        if (isPlaying) {
          handlePause();
        }
      } else if (interruption.type === 'end' && interruption.shouldResume) {
        // Resume playback if appropriate
        handlePlay();
      }
    });

    // Set up volume change listener
    const removeVolumeListener = MediaControl.addVolumeChangeListener((change: VolumeChange) => {
      console.log('🔊 Volume Change:', change);
      setVolume(change.volume);
      setLastEvent(`Volume: ${(change.volume * 100).toFixed(0)}% at ${new Date().toLocaleTimeString()}`);
    });

    // Cleanup listeners on unmount
    return () => {
      removeMediaListener();
      removeInterruptionListener();
      removeVolumeListener();
    };
  }, [isPlaying]);

  // =============================================
  // PLAYBACK CONTROL HANDLERS
  // =============================================
  
  const handlePlay = async () => {
    try {
      setIsPlaying(true);
      await MediaControl.updatePlaybackState(PlaybackState.PLAYING, currentPosition);
      console.log('▶️ Playing');
    } catch (error) {
      console.error('Failed to play:', error);
      Alert.alert('Error', 'Failed to update playback state');
    }
  };

  const handlePause = async () => {
    try {
      setIsPlaying(false);
      await MediaControl.updatePlaybackState(PlaybackState.PAUSED, currentPosition);
      console.log('⏸️ Paused');
    } catch (error) {
      console.error('Failed to pause:', error);
      Alert.alert('Error', 'Failed to update playback state');
    }
  };

  const handleStop = async () => {
    try {
      setIsPlaying(false);
      setCurrentPosition(0);
      await MediaControl.updatePlaybackState(PlaybackState.STOPPED, 0);
      console.log('⏹️ Stopped');
    } catch (error) {
      console.error('Failed to stop:', error);
      Alert.alert('Error', 'Failed to update playback state');
    }
  };

  const handleNextTrack = () => {
    const nextIndex = (currentTrackIndex + 1) % sampleTracks.length;
    switchTrack(nextIndex);
  };

  const handlePreviousTrack = () => {
    const prevIndex = currentTrackIndex === 0 ? sampleTracks.length - 1 : currentTrackIndex - 1;
    switchTrack(prevIndex);
  };

  const handleSkipForward = (interval: number) => {
    const newPosition = Math.min(currentPosition + interval, trackDuration);
    setCurrentPosition(newPosition);
    if (isPlaying) {
      MediaControl.updatePlaybackState(PlaybackState.PLAYING, newPosition);
    }
  };

  const handleSkipBackward = (interval: number) => {
    const newPosition = Math.max(currentPosition - interval, 0);
    setCurrentPosition(newPosition);
    if (isPlaying) {
      MediaControl.updatePlaybackState(PlaybackState.PLAYING, newPosition);
    }
  };

  const handleSeek = (position: number) => {
    const newPosition = Math.max(0, Math.min(position, trackDuration));
    setCurrentPosition(newPosition);
    if (isPlaying) {
      MediaControl.updatePlaybackState(PlaybackState.PLAYING, newPosition);
    }
  };

  const handleRating = (data: any) => {
    Alert.alert('Rating', `Received rating: ${JSON.stringify(data)}`);
  };

  const switchTrack = (index: number) => {
    setCurrentTrackIndex(index);
    const track = sampleTracks[index];
    setTrackTitle(track.title);
    setTrackArtist(track.artist);
    setTrackAlbum(track.album);
    setTrackDuration(track.duration);
    setCurrentPosition(0);
    
    // Update metadata immediately
    updateMetadata(track);
  };

  // =============================================
  // MEDIA CONTROL FUNCTIONS
  // =============================================
  
  const enableMediaControls = async () => {
    try {
      await MediaControl.enableMediaControls({
        capabilities: [
          Command.PLAY,
          Command.PAUSE,
          Command.STOP,
          Command.NEXT_TRACK,
          Command.PREVIOUS_TRACK,
          Command.SKIP_FORWARD,
          Command.SKIP_BACKWARD,
          Command.SEEK,
          Command.SET_RATING,
        ],
        notification: {
          icon: 'ic_music_note',
          color: '#2196F3',
          showWhenClosed: true,
          skipInterval: 15,
        },
        ios: {
          skipInterval: 15,
          showPlaybackPosition: true,
        },
        android: {
          requestAudioFocus: true,
          stopForegroundGracePeriod: 5000,
        },
      });
      
      setIsEnabled(true);
      
      // Set initial metadata
      await updateCurrentMetadata();
      
      console.log('✅ Media controls enabled');
      Alert.alert('Success', 'Media controls enabled successfully!');
    } catch (error) {
      console.error('Failed to enable media controls:', error);
      Alert.alert('Error', 'Failed to enable media controls');
    }
  };

  const disableMediaControls = async () => {
    try {
      await MediaControl.disableMediaControls();
      setIsEnabled(false);
      setIsPlaying(false);
      setCurrentPosition(0);
      console.log('❌ Media controls disabled');
      Alert.alert('Success', 'Media controls disabled');
    } catch (error) {
      console.error('Failed to disable media controls:', error);
      Alert.alert('Error', 'Failed to disable media controls');
    }
  };

  const updateCurrentMetadata = async () => {
    const track = sampleTracks[currentTrackIndex];
    await updateMetadata(track);
  };

  const updateMetadata = async (track?: any) => {
    try {
      const metadata = {
        title: track?.title || trackTitle,
        artist: track?.artist || trackArtist,
        album: track?.album || trackAlbum,
        duration: track?.duration || trackDuration,
        elapsedTime: currentPosition,
        genre: 'Rock',
        trackNumber: currentTrackIndex + 1,
        albumTrackCount: sampleTracks.length,
        date: '2024',
        artwork: showArtwork && track?.artwork ? {
          uri: track.artwork,
          width: 300,
          height: 300,
        } : undefined,
        rating: {
          type: RatingType.FIVE_STARS,
          value: 4.5,
          maxValue: 5,
        },
        color: '#2196F3',
        colorized: true,
      };
      
      await MediaControl.updateMetadata(metadata);
      console.log('🎵 Metadata updated');
    } catch (error) {
      console.error('Failed to update metadata:', error);
      Alert.alert('Error', 'Failed to update metadata');
    }
  };

  const resetControls = async () => {
    try {
      await MediaControl.resetControls();
      setCurrentPosition(0);
      setIsPlaying(false);
      console.log('🔄 Controls reset');
      Alert.alert('Success', 'Controls reset successfully');
    } catch (error) {
      console.error('Failed to reset controls:', error);
      Alert.alert('Error', 'Failed to reset controls');
    }
  };

  // =============================================
  // UTILITY FUNCTIONS
  // =============================================
  
  const formatTime = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const getCurrentTrack = () => sampleTracks[currentTrackIndex];

  // =============================================
  // RENDER COMPONENT
  // =============================================
  
  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>📱 Expo Media Control</Text>
          <Text style={styles.headerSubtitle}>Comprehensive Example</Text>
        </View>

        {/* Status Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Status</Text>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Controls Enabled:</Text>
            <Text style={[styles.statusValue, { color: isEnabled ? '#4CAF50' : '#F44336' }]}>
              {isEnabled ? '✅ Yes' : '❌ No'}
            </Text>
          </View>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Playback State:</Text>
            <Text style={[styles.statusValue, { color: isPlaying ? '#4CAF50' : '#FF9800' }]}>
              {isPlaying ? '▶️ Playing' : '⏸️ Paused'}
            </Text>
          </View>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Position:</Text>
            <Text style={styles.statusValue}>
              {formatTime(currentPosition)} / {formatTime(trackDuration)}
            </Text>
          </View>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Volume:</Text>
            <Text style={styles.statusValue}>{(volume * 100).toFixed(0)}%</Text>
          </View>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Last Event:</Text>
            <Text style={styles.eventText}>{lastEvent}</Text>
          </View>
        </View>

        {/* Control Buttons */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Media Controls</Text>
          <View style={styles.buttonRow}>
            <Button
              title={isEnabled ? "Disable Controls" : "Enable Controls"}
              onPress={isEnabled ? disableMediaControls : enableMediaControls}
              color={isEnabled ? "#F44336" : "#4CAF50"}
            />
          </View>
          <View style={styles.buttonRow}>
            <Button
              title="Reset Controls"
              onPress={resetControls}
              disabled={!isEnabled}
              color="#FF9800"
            />
          </View>
        </View>

        {/* Playback Controls */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Playback Controls</Text>
          <View style={styles.playbackControls}>
            <Button title="⏮️ Prev" onPress={handlePreviousTrack} disabled={!isEnabled} />
            <Button title="⏪ -15s" onPress={() => handleSkipBackward(15)} disabled={!isEnabled} />
            <Button
              title={isPlaying ? "⏸️ Pause" : "▶️ Play"}
              onPress={isPlaying ? handlePause : handlePlay}
              disabled={!isEnabled}
            />
            <Button title="⏩ +15s" onPress={() => handleSkipForward(15)} disabled={!isEnabled} />
            <Button title="⏭️ Next" onPress={handleNextTrack} disabled={!isEnabled} />
          </View>
          <View style={styles.buttonRow}>
            <Button title="⏹️ Stop" onPress={handleStop} disabled={!isEnabled} color="#F44336" />
          </View>
        </View>

        {/* Current Track Info */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Current Track</Text>
          {getCurrentTrack().artwork && showArtwork && (
            <Image source={{ uri: getCurrentTrack().artwork }} style={styles.artwork} />
          )}
          <Text style={styles.trackTitle}>{getCurrentTrack().title}</Text>
          <Text style={styles.trackArtist}>{getCurrentTrack().artist}</Text>
          <Text style={styles.trackAlbum}>{getCurrentTrack().album}</Text>
          <Text style={styles.trackInfo}>
            Track {currentTrackIndex + 1} of {sampleTracks.length} • {formatTime(getCurrentTrack().duration)}
          </Text>
        </View>

        {/* Track Selection */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Select Track</Text>
          {sampleTracks.map((track, index) => (
            <View key={index} style={styles.trackItem}>
              <Button
                title={`${index + 1}. ${track.title} - ${track.artist}`}
                onPress={() => switchTrack(index)}
                color={index === currentTrackIndex ? "#2196F3" : "#6c757d"}
              />
            </View>
          ))}
        </View>

        {/* Settings */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Settings</Text>
          <View style={styles.settingRow}>
            <Text style={styles.settingLabel}>Show Artwork:</Text>
            <Switch
              value={showArtwork}
              onValueChange={(value) => {
                setShowArtwork(value);
                if (isEnabled) updateCurrentMetadata();
              }}
            />
          </View>
        </View>

        {/* Custom Metadata Input */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Custom Track Info</Text>
          <TextInput
            style={styles.input}
            value={trackTitle}
            onChangeText={setTrackTitle}
            placeholder="Track Title"
            onEndEditing={() => isEnabled && updateCurrentMetadata()}
          />
          <TextInput
            style={styles.input}
            value={trackArtist}
            onChangeText={setTrackArtist}
            placeholder="Artist"
            onEndEditing={() => isEnabled && updateCurrentMetadata()}
          />
          <TextInput
            style={styles.input}
            value={trackAlbum}
            onChangeText={setTrackAlbum}
            placeholder="Album"
            onEndEditing={() => isEnabled && updateCurrentMetadata()}
          />
          <TextInput
            style={styles.input}
            value={trackDuration.toString()}
            onChangeText={(text) => setTrackDuration(parseInt(text) || 180)}
            placeholder="Duration (seconds)"
            keyboardType="numeric"
            onEndEditing={() => isEnabled && updateCurrentMetadata()}
          />
          <View style={styles.buttonRow}>
            <Button
              title="Update Metadata"
              onPress={updateCurrentMetadata}
              disabled={!isEnabled}
              color="#2196F3"
            />
          </View>
        </View>

      </ScrollView>
    </SafeAreaView>
  );
}

// =============================================
// STYLES
// =============================================

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollView: {
    flex: 1,
  },
  content: {
    paddingBottom: 20,
  },
  header: {
    backgroundColor: '#2196F3',
    padding: 20,
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: 'white',
    marginBottom: 5,
  },
  headerSubtitle: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.8)',
  },
  section: {
    backgroundColor: 'white',
    margin: 10,
    borderRadius: 10,
    padding: 15,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 15,
    color: '#333',
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  statusLabel: {
    fontSize: 14,
    color: '#666',
    fontWeight: '500',
  },
  statusValue: {
    fontSize: 14,
    fontWeight: 'bold',
  },
  eventText: {
    fontSize: 12,
    color: '#666',
    fontStyle: 'italic',
    flex: 1,
    textAlign: 'right',
  },
  buttonRow: {
    marginBottom: 10,
  },
  playbackControls: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  artwork: {
    width: 100,
    height: 100,
    borderRadius: 10,
    alignSelf: 'center',
    marginBottom: 10,
  },
  trackTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 5,
  },
  trackArtist: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 5,
  },
  trackAlbum: {
    fontSize: 14,
    color: '#888',
    textAlign: 'center',
    marginBottom: 5,
  },
  trackInfo: {
    fontSize: 12,
    color: '#aaa',
    textAlign: 'center',
  },
  trackItem: {
    marginBottom: 5,
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  settingLabel: {
    fontSize: 16,
    color: '#333',
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 5,
    padding: 10,
    marginBottom: 10,
    fontSize: 16,
  },
});
