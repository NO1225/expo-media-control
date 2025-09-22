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
// import {
//   MediaControl,
//   PlaybackState,
//   Command,
//   RatingType,
//   MediaControlEvent,
//   AudioInterruption,
//   VolumeChange,
// } from 'expo-media-control';
import { PlayerManager, Audio } from './PlayerManager';
  const playerManager = PlayerManager.getInstance();

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
  
  const [isEnabled, setIsEnabled] = useState(true);
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
      id: '1',
      title: 'Test Artwork Track',
      artist: 'RadDy Questions',
      album: 'Logo Test Album',
      artwork: 'https://via.placeholder.com/300x300/4CAF50/FFFFFF?text=Nature',
      url: 'https://dl.espressif.com/dl/audio/ff-16b-2c-44100hz.mp3'
    },
    {
      id: '2',
      title: 'Nature Sounds',
      artist: 'SoundJay',
      album: 'Free Audio Samples',
      artwork: 'https://via.placeholder.com/300x300/4CAF50/FFFFFF?text=Nature',
      url: 'https://download.samplelib.com/mp3/sample-15s.mp3'
    },
    {
      id: '3',
      title: 'Piano Sample',
      artist: 'Demo Artist',
      album: 'Demo Album',
      artwork: 'https://via.placeholder.com/300x300/2196F3/FFFFFF?text=Piano',
      url: 'https://download.samplelib.com/mp3/sample-15s.mp3'
    }
  ];
  const [currentTrackIndex, setCurrentTrackIndex] = useState(0);

  // =============================================
  // PLAYER MANAGER SETUP
  // =============================================
  

  // =============================================
  // EVENT HANDLERS SETUP
  // =============================================
  
  // Initialize PlayerManager
  useEffect(() => {
    playerManager.init();
    
    // Set up PlayerManager callbacks to sync with UI state
    playerManager.onIsPlayingChanged = (id: string, isPlaying: boolean) => {
      console.log('🎵 PlayerManager playing state changed:', isPlaying);
      setIsPlaying(isPlaying);
    };
    
    playerManager.onIsLoadingChanged = (isLoading: boolean) => {
      // Could add a loading state here if needed
      console.log('Player loading state:', isLoading);
    };
    
    playerManager.onProgressUpdated = (id: string, currentTime: number, duration: number) => {
      setCurrentPosition(currentTime);
      setTrackDuration(duration);
    };
    
    playerManager.onItemChanged = (newItem: Audio) => {
      // Find the index of the new item and update UI
      const newIndex = sampleTracks.findIndex(track => track.id === newItem.id);
      if (newIndex >= 0) {
        setCurrentTrackIndex(newIndex);
        const track = sampleTracks[newIndex];
        setTrackTitle(track.title);
        setTrackArtist(track.artist);
        setTrackAlbum(track.album);
        setCurrentPosition(0);
        
        // Update MediaControl metadata when track changes
      }
    };
    
    playerManager.onItemCompleted = (id: string) => {
      console.log('Track completed:', id);
      // PlayerManager will handle skipping to next automatically
    };
    
    // Load the playlist
    playerManager.loadPlayList(sampleTracks, false, sampleTracks[0].id);

    
    return () => {
      playerManager.clearAudio();
    };
  }, []);
  
  
  // =============================================
  // PLAYBACK CONTROL HANDLERS
  // =============================================
  
  const handlePlay = async () => {
    try {
      playerManager.play();

      console.log('▶️ Playing');
    } catch (error) {
      console.error('Failed to play:', error);
      Alert.alert('Error', 'Failed to update playback state');
    }
  };

  const handlePause = async () => {
    try {
      playerManager.pause();
      console.log('⏸️ Paused');
    } catch (error) {
      console.error('Failed to pause:', error);
      Alert.alert('Error', 'Failed to update playback state');
    }
  };

  const handleStop = async () => {
    try {
      // PlayerManager doesn't have a direct stop method, so we'll pause and seek to start
      playerManager.stop();
      setCurrentPosition(0);
      console.log('⏹️ Stopped');
    } catch (error) {
      console.error('Failed to stop:', error);
      Alert.alert('Error', 'Failed to update playback state');
    }
  };

  const handleNextTrack = () => {
    playerManager.skipNext();
  };

  const handlePreviousTrack = () => {
    playerManager.skipPrev();
  };

  const handleSkipForward = (interval: number) => {
    const currentTime = playerManager.getCurrentTime();
    const duration = playerManager.getDuration();
    const newPosition = Math.min(currentTime + interval, duration);
    playerManager.seekTo(newPosition / duration);

  };

  const handleSkipBackward = (interval: number) => {
    const currentTime = playerManager.getCurrentTime();
    const duration = playerManager.getDuration();
    const newPosition = Math.max(currentTime - interval, 0);
    playerManager.seekTo(newPosition / duration);

  };

  const handleSeek = (position: number) => {
    const duration = playerManager.getDuration();
    const newPosition = Math.max(0, Math.min(position, duration));
    playerManager.seekTo(newPosition / duration);
    setCurrentPosition(newPosition);

  };

  const handleRating = (data: any) => {
    Alert.alert('Rating', `Received rating: ${JSON.stringify(data)}`);
  };

  const switchTrack = (index: number) => {
    if (index >= 0 && index < sampleTracks.length) {
      setCurrentTrackIndex(index);
      const track = sampleTracks[index];
      setTrackTitle(track.title);
      setTrackArtist(track.artist);
      setTrackAlbum(track.album);
      setCurrentPosition(0);
      
      // Tell PlayerManager to start playing this track
      playerManager.startPlayingAtId(track.id);
      
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
              }}
            />
          </View>
        </View>

        {/* Custom Metadata Input 
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
*/}
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
