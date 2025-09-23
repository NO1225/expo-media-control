import { AudioPlayer, createAudioPlayer, setAudioModeAsync } from "expo-audio";

import {
    MediaControl,
    PlaybackState,
    Command,
    MediaControlEvent,
    AudioInterruption,
    VolumeChange,
    MediaMetadata
} from 'expo-media-control';
import { Platform } from "react-native";

const UPDATE_INTERVAL = 500;

export class Audio {
    id: string;
    title: string;
    album?: string;
    albumTrackCount?: number;
    trackNumber?: number;
    url: string;
    duration?: number;
    onDurationChanged?: (id: string, duration: number) => void;
    private setDuration(duration: number) {
        // if not changed, ignore
        if (this.duration === duration)
            return;
        this.duration = duration;
        this.onDurationChanged?.(this.id, duration);
    }

    onIsPlayingChanged?: (id: string, isPlaying: boolean) => void;
    onIsLoadingChanged?: (isLoading: boolean) => void;

    onProgressUpdated?: (id: string, currentTime: number, duration: number) => void;
    private setCurrentProgress(currentTime: number, duration: number) {
        this.onProgressUpdated?.(this.id, currentTime, duration);
        this.setDuration(duration);
    }

    onItemCompleted?: (id: string) => void;
    onItemChanged?: (newItem: Audio) => void;

    player: AudioPlayer;

    next: Audio | null;
    prev: Audio | null;

    constructor(id: string, url: string, title: string, album?: string, albumTrackCount?: number, trackNumber?: number) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.album = album;
        this.albumTrackCount = albumTrackCount;
        this.trackNumber = trackNumber;
        this.player = createAudioPlayer(url, {
            updateInterval: UPDATE_INTERVAL
        });
        this.next = null;
        this.prev = null;

        this.player.addListener('playbackStatusUpdate', (status) => {
            this.onIsPlayingChanged?.(this.id, status.playing);
            this.setCurrentProgress(status.currentTime, status.duration);

            if (status.isBuffering)
                this.onIsLoadingChanged?.(true);

            if (status.isLoaded) {
                this.onIsLoadingChanged?.(false);
                this.setCurrentProgress(status.currentTime, status.duration);

                if (status.didJustFinish) {
                    this.onItemCompleted?.(this.id);
                }
            } else {
                if (status.reasonForWaitingToPlay) {
                    console.log(`FATAL PLAYER ERROR: ${status.reasonForWaitingToPlay}`);
                }
            }
        });
    }

    setNext(next: Audio) {
        this.next = next;
        next.prev = this;
    }

    setPrev(prev: Audio) {
        this.prev = prev;
        prev.next = this;
    }

    setRate(rate: number) {
        if (Platform.OS === "android") {
            this.player.shouldCorrectPitch = true;
            this.player.setPlaybackRate(rate);
        } else {
            this.player.setPlaybackRate(rate, "high");
        }
        console.log(`Set playback rate: ${rate}`);
    }

    play(rate: number, startAt0: boolean = false) {
        if (startAt0 || this.player.currentTime >= this.player.duration) {
            this.seekTo(0);
            this.onItemChanged?.(this);
        }

        this.player.play();
        this.setRate(rate);
    }

    pause() {
        this.player.pause();
    }

    stop() {
        this.player.seekTo(0);
        this.player.pause();
    }

    seekTo(ratio: number) {
        this.player.seekTo(this.player.duration * ratio);
    }

    // on destroy, release player
    destroy() {
        this.player.release();
    }
}

export class PlayerManager {
    public static instance: PlayerManager;
    notificationId?: string;

    public static getInstance(): PlayerManager {
        if (!PlayerManager.instance) {
            PlayerManager.instance = new PlayerManager();
            console.log("Created PlayerManager instance");
        }
        return PlayerManager.instance;
    }

    private audios: Audio[] = [];
    private activeAudio: Audio | null = null;
    private setActiveAudio(audio: Audio) {
        console.log(`Setting active audio: ${audio.id} - ${audio.url}`);
        this.activeAudio?.stop();

        this.activeAudio = audio;
        this.onItemChanged?.(audio);

        // Clean metadata before sending to native module
        const metadata: MediaMetadata = {
            title: audio.title || 'Unknown Title',
            artist: audio.album || 'Unknown Artist',
            album: audio.album || 'Unknown Album',
        };

        // Only add numeric values if they're valid
        const duration = audio.duration ?? audio?.player?.duration;
        if (typeof duration === 'number' && duration > 0) {
            metadata.duration = duration;
        }

        if (typeof audio.albumTrackCount === 'number' && audio.albumTrackCount > 0) {
            metadata.albumTrackCount = audio.albumTrackCount;
        }

        if (typeof audio.trackNumber === 'number' && audio.trackNumber > 0) {
            metadata.trackNumber = audio.trackNumber;
        }

        MediaControl.updateMetadata(metadata).catch(error => {
            console.error('Failed to update MediaControl metadata from setActiveAudio:', error);
        })
    }

    startPlayingAtId(id: string) {
        const audio = this.audios.find(audio => audio.id === id);
        if (audio) {
            this.setActiveAudio(audio);
            this.play();
        }
    }

    private rate: number = 1;

    isPlaying: boolean = false;
    onIsPlayingChanged?: (id: string, isPlaying: boolean) => void;
    private setIsPlaying(isPlaying: boolean) {
        if (this.isPlaying !== isPlaying) {
            MediaControl.updatePlaybackState(isPlaying ? PlaybackState.PLAYING : PlaybackState.PAUSED, this.activeAudio?.player.currentTime ?? 0).catch(error => {
                console.error('Failed to update MediaControl playback state from isPlaying change:', error);
            });
        }

        this.isPlaying = isPlaying;
        if (this.activeAudio)
            this.onIsPlayingChanged?.(this.activeAudio.id, isPlaying);
    }

    isLoading: boolean = false;
    onIsLoadingChanged?: (isLoading: boolean) => void;
    private setIsLoading(isLoading: boolean) {
        this.isLoading = isLoading;

        if (this.activeAudio)
            this.onIsLoadingChanged?.(isLoading);
    }

    onProgressUpdated?: (id: string, currentTime: number, duration: number) => void;
    private setCurrentProgress(currentTime: number, duration: number) {
        if (this.activeAudio) {
            this.onProgressUpdated?.(this.activeAudio.id, currentTime, duration);

        }
    }

    onItemCompleted?: (id: string) => void;
    onItemChanged?: (newItem: Audio) => void;

    async init() {
        console.log("Initializing PlayerManager");
        setAudioModeAsync({
            interruptionMode: "doNotMix",
            interruptionModeAndroid: "doNotMix",
            shouldPlayInBackground: true,
            shouldRouteThroughEarpiece: true,
            playsInSilentMode: true
        });

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
            ],
            notification: {
                icon: 'ic_music_note',
                color: '#2196F3',
                showWhenClosed: true,
                skipInterval: 15,
            },
            ios: {
                skipInterval: 15,
            },
            android: {
                requestAudioFocus: true,
            },
        });

        this._registerCommands();
    }

    loadPlayList(playList: {
        id: string;
        title: string;
        url: string;
        album?: string;
        albumTrackCount?: number;
        trackNumber?: number;
    }[], autoStart: boolean, startWithId?: string) {
        this.clearAudio();

        this.audios = playList.map(item => new Audio(item.id, item.url, item.title, item.album, item.albumTrackCount, item.trackNumber));

        for (let i = 0; i < this.audios.length; i++) {
            const audioItem = this.audios[i];

            if (i < this.audios.length - 1) {
                audioItem.setNext(this.audios[i + 1]);
            }

            audioItem.onIsPlayingChanged = (id, isPlaying) => {
                // console.log(`Audio item isPlaying changed: ${id} - ${isPlaying}`);
                if (this.activeAudio !== audioItem)
                    return;
                this.setIsPlaying?.(isPlaying);

            };

            audioItem.onIsLoadingChanged = (isLoading) => {
                if (this.activeAudio !== audioItem)
                    return;
                this.setIsLoading?.(isLoading);
            };

            audioItem.onProgressUpdated = (id, currentTime, duration) => {
                if (this.activeAudio !== audioItem)
                    return;
                this.setCurrentProgress?.(currentTime, duration);
            };

            audioItem.onDurationChanged = (id, duration) => {
                if (this.activeAudio !== audioItem)
                    return;

                if (!duration || duration <= 0 || typeof duration !== 'number') return;

                // Clean metadata before sending to native module
                const metadata: MediaMetadata = {
                    title: audioItem.title || 'Unknown Title',
                    duration: duration,
                    artist: audioItem.album || 'Unknown Artist',
                    album: audioItem.album || 'Unknown Album',
                };

                if (typeof audioItem.albumTrackCount === 'number' && audioItem.albumTrackCount > 0) {
                    metadata.albumTrackCount = audioItem.albumTrackCount;
                }

                if (typeof audioItem.trackNumber === 'number' && audioItem.trackNumber > 0) {
                    metadata.trackNumber = audioItem.trackNumber;
                }

                MediaControl.updateMetadata(metadata).catch(error => {
                    console.error('Failed to update MediaControl metadata from duration update:', error);
                });
            };

            audioItem.onItemCompleted = (id) => {
                if (this.activeAudio !== audioItem)
                    return;
                this.onItemCompleted?.(audioItem.id);
                this.skipNext();
            };

            audioItem.onItemChanged = (newItem) => {
                if (this.activeAudio !== audioItem)
                    return;
                console.log(`Audio item changed: ${newItem.id}`);
                this.setActiveAudio(newItem);
            };
        }

        if (startWithId) {
            const startAudio = this.audios.find(audio => audio.id === startWithId);
            if (startAudio) {
                this.setActiveAudio(startAudio);
            }
        } else {
            this.setActiveAudio(this.audios[0]);
        }

        if (autoStart) {
            this.play();
        }
    }

    async clearAudio() {
        for (const audio of this.audios) {
            audio.destroy();
        }
        this.audios = [];
        this.activeAudio = null;

        console.log('Cleared audio:', this)
    }

    destroy() {
        this._unregisterCommands();
        MediaControl.disableMediaControls();
    }


    setRate(rate: number) {
        this.rate = rate;
        this.activeAudio?.setRate(rate);
    }

    play() {
        this.activeAudio?.play(this.rate);
    }

    pause() {
        this.activeAudio?.pause();
    }

    stop() {
        this.activeAudio?.stop();
        // this.setIsPlaying(false);
        // this.setCurrentProgress(0, this.getDuration());
    }

    seekTo(ratio: number) {
        this.activeAudio?.seekTo(ratio);
    }

    skipNext() {
        if (this.activeAudio?.next) {
            this.changeItem(this.activeAudio.next);
        }
    }

    skipPrev() {
        if (this.activeAudio?.prev) {
            this.changeItem(this.activeAudio.prev);
        }
    }

    getCurrentTime(): number {
        return this.activeAudio?.player?.currentTime || 0;
    }

    getDuration(): number {
        return this.activeAudio?.player?.duration || 0;
    }

    private changeItem(newItem: Audio) {
        this.setActiveAudio(newItem);
        this.activeAudio?.play(this.rate, true);
    }

    private _removeListenersListener: (() => void) | null = null;
    private _removeInterruptionListener: (() => void) | null = null;
    private _removeVolumeListener: (() => void) | null = null;
    private _registerCommands() {
        // Set up media control event listener
        console.log('📱 JS: Setting up media control event listener');
        this._removeListenersListener?.();
        this._removeListenersListener = MediaControl.addListener((event: MediaControlEvent) => {
            console.log('📱 JS: Media Control Event received:', event);
            console.log('📱 JS: Event command:', event.command);
            console.log('📱 JS: Event timestamp:', event.timestamp);

            // return;

            // Handle different commands - delegate to PlayerManager with immediate responses
            switch (event.command) {
                case Command.PLAY:
                    console.log('🎵 Remote PLAY command received');
                    // Direct response for remote control - don't wait for React state updates
                    this.play();
                    // // Update MediaControl state immediately
                    // setTimeout(() => {
                    //     this.play();
                    // }, 200);
                    break;
                case Command.PAUSE:
                    console.log('🎵 Remote PAUSE command received');
                    // Direct response for remote control - don't wait for React state updates
                    this.pause();
                    // Update MediaControl state immediately
                    // setTimeout(() => {
                    //     this.pause();
                    // }, 200);
                    break;
                case Command.STOP:
                    console.log('🎵 Remote STOP command received');
                    this.stop();

                    // handleStop();
                    break;
                case Command.NEXT_TRACK:
                    console.log('🎵 Remote NEXT_TRACK command received');
                    this.skipNext();
                    break;
                case Command.PREVIOUS_TRACK:
                    console.log('🎵 Remote PREVIOUS_TRACK command received');
                    this.skipPrev();
                    break;
                case Command.SKIP_FORWARD:
                    // Skip forward by interval (convert seconds to ratio)
                    const currentTime = this.activeAudio?.player?.currentTime || 0;
                    const duration = this.activeAudio?.player?.duration || 1;
                    const interval = event.data?.interval || 15;
                    const newPosition = Math.min(currentTime + interval, duration);
                    this.seekTo(newPosition / duration);
                    break;
                case Command.SKIP_BACKWARD:
                    // Skip backward by interval (convert seconds to ratio)
                    const currentTimeBack = this.activeAudio?.player?.currentTime || 0;
                    const durationBack = this.activeAudio?.player?.duration || 1;
                    const intervalBack = event.data?.interval || 15;
                    const newPositionBack = Math.max(currentTimeBack - intervalBack, 0);
                    this.seekTo(newPositionBack / durationBack);
                    break;
                case Command.SEEK:
                    if (event.data?.position !== undefined) {
                        // Convert absolute position to ratio
                        const seekDuration = this.activeAudio?.player?.duration || 1;
                        this.seekTo(event.data.position / seekDuration);
                    }
                    break;
                // case Command.SET_RATING:
                //     handleRating(event.data);
                //     break;
            }
        });

        this._removeInterruptionListener?.();
        // Set up audio interruption listener
        this._removeInterruptionListener = MediaControl.addAudioInterruptionListener((interruption: AudioInterruption) => {
            console.log('🔊 Audio Interruption:', interruption);
            if (interruption.type === 'begin') {
                this.pause();
            } else if (interruption.type === 'end' && interruption.shouldResume) {
                // Resume playback if appropriate
                this.play();
            }
        });

        // Set up volume change listener
        this._removeVolumeListener = MediaControl.addVolumeChangeListener((change: VolumeChange) => {
            console.log('🔊 Volume Change:', change);
            // setVolume(change.volume);
            // setLastEvent(`Volume: ${(change.volume * 100).toFixed(0)}% at ${new Date().toLocaleTimeString()}`);
        });

    }
    private _unregisterCommands() {
        console.log('📱 JS: Removing media control event listener');
        this._removeListenersListener?.();
        this._removeListenersListener = null;

        this._removeInterruptionListener?.();
        this._removeInterruptionListener = null;

        this._removeVolumeListener?.();
        this._removeVolumeListener = null;
    }

}
