import { AudioPlayer, createAudioPlayer, setAudioModeAsync } from "expo-audio";

import {
    MediaControl,
    PlaybackState,
    Command,
    MediaControlEvent,
    VolumeChange,
} from 'expo-media-control';
import { Platform } from "react-native";

const UPDATE_INTERVAL = 500;
const MAX_INITIALIZED_AUDIOS = 5; // limit the number of initialized audio players to avoid memory issues

class Audio {
    id: string;
    title: string;
    album?: string;
    albumTrackCount?: number;
    trackNumber?: number;
    url: string;
    duration?: number;
    artWork?: string;
    onDurationChanged?: (id: string, duration: number) => void;
    private setDuration(duration: number) {
        // if not changed, ignore
        if (this.duration === duration)
            return;
        this.duration = duration;
        this.onDurationChanged?.(this.id, duration);
    }

    getDuration(): number {
        return this.duration ?? this.player?.duration ?? 0;
    }
    getCurrentTime(): number {
        return this.player?.currentTime ?? 0;
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

    private player?: AudioPlayer;
    initPlayer() {
        if (!this.player) {
            this.player = createAudioPlayer(this.url, {
                updateInterval: UPDATE_INTERVAL,
            });

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

        return this.id;
    }
    releasePlayer() {
        this.player?.removeAllListeners('playbackStatusUpdate');
        this.player?.release();
        this.player = undefined;
    }

    next: Audio | null;
    prev: Audio | null;

    constructor(id: string, url: string, title: string, album?: string, albumTrackCount?: number, trackNumber?: number, artWork?: string) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.album = album;
        this.albumTrackCount = albumTrackCount;
        this.artWork = artWork;
        this.trackNumber = trackNumber;

        this.next = null;
        this.prev = null;
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
        if (!this.player) {
            throw new Error("Player not initialized");
        }

        if (Platform.OS === "android") {
            this.player.shouldCorrectPitch = true;
            this.player.setPlaybackRate(rate);
        } else {
            this.player.setPlaybackRate(rate, "high");
        }
        console.log(`Set playback rate: ${rate}`);
    }

    play(rate: number, startAt0: boolean = false) {
        if (!this.player) {
            throw new Error("Player not initialized");
        }

        if (startAt0 || this.player.currentTime >= this.player.duration) {
            this.seekTo(0);
            this.onItemChanged?.(this);
        }

        this.player.play();
        this.setRate(rate);
    }

    pause() {
        if (!this.player) {
            throw new Error("Player not initialized");
        }
        this.player.pause();
    }

    stop() {
        if (!this.player) {
            throw new Error("Player not initialized");
        }
        this.player.seekTo(0);
        this.player.pause();
    }

    seekTo(ratio: number) {
        if (!this.player) {
            throw new Error("Player not initialized");
        }
        this.player.seekTo(this.player.duration * ratio);
    }

    // on destroy, release player
    destroy() {
        this.releasePlayer();
        this.onIsPlayingChanged = undefined;
        this.onIsLoadingChanged = undefined;
        this.onProgressUpdated = undefined;
        this.onItemCompleted = undefined;
        this.onItemChanged = undefined;

        this.next = null;
        this.prev = null;

        console.log(`Destroyed Audio: ${this.id} - ${this.url}`);
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

    private initializedAudios: Audio[] = [];
    private initPlayer(audio: Audio, nextBuffer?: number) {
        console.log(`Request to init player for audio: ${audio.id}`);
        // print current initialized audios number
        console.log(`Currently initialized audios: ${this.initializedAudios.map(a => a.id).join(", ")}`);

        if (this.initializedAudios.length >= MAX_INITIALIZED_AUDIOS) {
            const audioToRelease = this.initializedAudios.shift();
            audioToRelease?.releasePlayer();
            console.log(`Released least recently used audio player: ${audioToRelease?.id}`);
        }
        if (!this.initializedAudios.includes(audio)) {
            audio.initPlayer();
            this.initializedAudios.push(audio);
            console.log(`Initialized audio player: ${audio.id}`);
        }

        // pre-initialize next audio if specified recursively
        if (nextBuffer && nextBuffer > 0 && audio.next) {
            this.initPlayer(audio.next, nextBuffer - 1);
        }
    }

    private audios: Audio[] = [];
    private activeAudio: Audio | null = null;
    private setActiveAudio(audio: Audio) {
        console.log(`Setting active audio: ${audio.id} - ${audio.url}`);
        this.activeAudio?.stop();

        this.activeAudio = audio;
        this.initPlayer(this.activeAudio, 1);
        // notify change
        this.onItemChanged?.(audio);

        console.log(`Active audio set: ${audio.id} - ${audio.artWork}`);
        MediaControl.updateMetadata({
            title: audio.title,
            duration: audio.getDuration(),
            artist: audio.album,
            album: audio.album,
            albumTrackCount: audio.albumTrackCount,
            trackNumber: audio.trackNumber,
            artwork: audio.artWork? {
                uri: audio.artWork,
                width: 512,
                height:512
            }: undefined,
        }).then(()=>{
            MediaControl.updatePlaybackState(
                PlaybackState.PLAYING,
                0,
                this.rate
            )
        }).catch(error => {
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
            MediaControl.updatePlaybackState(
                isPlaying ? PlaybackState.PLAYING : PlaybackState.PAUSED,
                this.activeAudio?.getCurrentTime() ?? 0,
                isPlaying ? this.rate : 0.0  // Rate = this.rate when playing, 0.0 when paused
            ).catch(error => {
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
        // Only update MediaControl when buffering state changes
        if (this.isLoading !== isLoading) {
            this.isLoading = isLoading;

            // Update MediaControl with buffering state
            const state = isLoading
                ? PlaybackState.BUFFERING
                : (this.isPlaying ? PlaybackState.PLAYING : PlaybackState.PAUSED);

            MediaControl.updatePlaybackState(
                state,
                this.activeAudio?.getCurrentTime() ?? 0,
                (this.isPlaying && !isLoading) ? this.rate : 0.0
            ).catch(error => {
                console.error('Failed to update MediaControl buffering state:', error);
            });

            if (this.activeAudio)
                this.onIsLoadingChanged?.(isLoading);
        } else {
            this.isLoading = isLoading;
            if (this.activeAudio)
                this.onIsLoadingChanged?.(isLoading);
        }
    }

    onProgressUpdated?: (id: string, currentTime: number, duration: number) => void;
    private setCurrentProgress(currentTime: number, duration: number) {
        if (this.activeAudio) {
            // Update JavaScript UI only - native platforms handle progress animation automatically
            this.onProgressUpdated?.(this.activeAudio.id, currentTime, duration);

            // NO MediaControl.updatePlaybackState() here!
            // Native iOS/Android animate progress smoothly based on the playback rate.
            // Calling updatePlaybackState every 500ms would interrupt the smooth native animation.
            // Only update MediaControl when state actually changes (play/pause/rate/buffering/seek)
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
            compactCapabilities: [
                Command.PREVIOUS_TRACK,
                Command.PLAY,
                Command.NEXT_TRACK,
            ],
            notification: {
                // icon: 'ic_music_note', // For bare workflow only
                color: '#2196F3',
                showWhenClosed: true,
            },
            ios: {
                skipInterval: 15,
            },
            android: {
                skipInterval: 15,
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
        artWork?: string;
    }[], autoStart: boolean, startWithId?: string) {
        this.clearAudio();

        this.audios = playList.map(item => new Audio(item.id, item.url, item.title, item.album, item.albumTrackCount, item.trackNumber, item.artWork));
        console.log(`Loaded playlist with ${this.audios.length} items`);
        // log all the audio infos 
        console.log('Playlist items:');
        playList.forEach(audio => {
            console.log(`- ID: ${audio.id}, Title: ${audio.title}, URL: ${audio.url}, Album: ${audio.album}, Track#: ${audio.trackNumber}, ArtWork: ${audio.artWork}`);
        });
        for (let i = 0; i < this.audios.length; i++) {
            const audioItem = this.audios[i];

            if (i < this.audios.length - 1) {
                audioItem.setNext(this.audios[i + 1]);
            }

            audioItem.onIsPlayingChanged = (id, isPlaying) => {
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

                if (!duration || duration <= 0) return;
                console.log(`Active audio set: ${audioItem.id} - ${audioItem.artWork}`);
                MediaControl.updateMetadata({
                    title: audioItem.title,
                    duration: duration,
                    artist: audioItem.album,
                    album: audioItem.album,
                    albumTrackCount: audioItem.albumTrackCount,
                    trackNumber: audioItem.trackNumber,
                    artwork: audioItem.artWork? {
                        uri: audioItem.artWork,
                        width: 512,
                        height:512
                    }: undefined,
                }).catch(error => {
                    console.error('Failed to update MediaControl metadata from duration update:', error);
                });
            };

            audioItem.onItemCompleted = (id) => {
                if (this.activeAudio !== audioItem)
                    return;
                console.log(`Audio item completed: ${id}`);
                const [_, resourceIdStr] = id.split(',');
                const resourceId = parseInt(resourceIdStr);
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

        if (this.activeAudio)
            this.initPlayer(this.activeAudio, MAX_INITIALIZED_AUDIOS / 2); // pre-initialize next few audios

        if (autoStart)
            this.play();
    }

    async clearAudio() {
        for (const audio of this.audios) {
            audio.destroy();
        }
        this.audios = [];
        this.activeAudio = null;
        this.initializedAudios = [];

        console.log('Cleared audio:', this)
    }

    destroy() {
        console.log("Destroying PlayerManager");
        this.clearAudio();

        this._unregisterCommands();
        MediaControl.disableMediaControls();
    }


    setRate(rate: number) {
        this.rate = rate;
        this.activeAudio?.setRate(rate);

        // Immediately update native media controls with the new rate
        // Only pass the actual rate if playing; pass 0.0 if paused (platform convention)
        if (this.activeAudio) {
            MediaControl.updatePlaybackState(
                this.isPlaying ? PlaybackState.PLAYING : PlaybackState.PAUSED,
                this.activeAudio.getCurrentTime(),
                this.isPlaying ? rate : 0.0
            ).catch(error => {
                console.error('Failed to update MediaControl playback state after rate change:', error);
            });
        }
    }

    play() {
        this.activeAudio?.play(this.rate);
    }

    pause() {
        this.activeAudio?.pause();
    }

    stop() {
        this.activeAudio?.stop();
    }

    seekTo(ratio: number) {
        this.activeAudio?.seekTo(ratio);

        // Update MediaControl when user seeks (position jumped unexpectedly)
        if (this.activeAudio) {
            const newPosition = this.activeAudio.getDuration() * ratio;
            MediaControl.updatePlaybackState(
                this.isPlaying ? PlaybackState.PLAYING : PlaybackState.PAUSED,
                newPosition,
                this.isPlaying ? this.rate : 0.0
            ).catch(error => {
                console.error('Failed to update MediaControl playback state after seek:', error);
            });
        }
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
        return this.activeAudio?.getCurrentTime() ?? 0;
    }
    
    getDuration(): number {
        return this.activeAudio?.getDuration() ?? 0;
    }

    private changeItem(newItem: Audio) {
        this.setActiveAudio(newItem);
        this.activeAudio?.play(this.rate, true);
    }

    private _removeListenersListener: (() => void) | null = null;
    private _removeVolumeListener: (() => void) | null = null;
    private _registerCommands() {
        // Set up media control event listener
        console.log('📱 JS: Setting up media control event listener');
        this._removeListenersListener?.();
        this._removeListenersListener = MediaControl.addListener((event: MediaControlEvent) => {
            console.log('📱 JS: Media Control Event received:', event);
            console.log('📱 JS: Event command:', event.command);
            console.log('📱 JS: Event timestamp:', event.timestamp);

            // Handle different commands - delegate to PlayerManager with immediate responses
            switch (event.command) {
                case Command.PLAY:
                    console.log('🎵 Remote PLAY command received');
                    this.play();
                    break;
                case Command.PAUSE:
                    console.log('🎵 Remote PAUSE command received');
                    this.pause();
                    break;
                case Command.STOP:
                    console.log('🎵 Remote STOP command received');
                    this.stop();
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
                    const currentTime = this.activeAudio?.getCurrentTime() || 0;
                    const duration = this.activeAudio?.getDuration() || 1;
                    const interval = event.data?.interval || 15;
                    const newPosition = Math.min(currentTime + interval, duration);
                    this.seekTo(newPosition / duration);
                    break;
                case Command.SKIP_BACKWARD:
                    // Skip backward by interval (convert seconds to ratio)
                    const currentTimeBack = this.activeAudio?.getCurrentTime() || 0;
                    const durationBack = this.activeAudio?.getDuration() || 1;
                    const intervalBack = event.data?.interval || 15;
                    const newPositionBack = Math.max(currentTimeBack - intervalBack, 0);
                    this.seekTo(newPositionBack / durationBack);
                    break;
                case Command.SEEK:
                    if (event.data?.position !== undefined) {
                        // Convert absolute position to ratio
                        const seekDuration = this.activeAudio?.getDuration() || 1;
                        this.seekTo(event.data.position / seekDuration);
                    }
                    break;
                // case Command.SET_RATING:
                //     handleRating(event.data);
                //     break;
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

        this._removeVolumeListener?.();
        this._removeVolumeListener = null;
    }

}
