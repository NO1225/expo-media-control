import { AudioPlayer, createAudioPlayer, setAudioModeAsync } from "expo-audio";

const UPDATE_INTERVAL = 1000;

class Audio {
    id: string;
    title: string;
    url: string;

    onIsPlayingChanged?: (id: string, isPlaying: boolean) => void;
    onIsLoadingChanged?: (isLoading: boolean) => void;

    onProgressUpdated?: (id: string, currentTime: number, duration: number) => void;
    private setCurrentProgress(currentTime: number, duration: number) {
        this.onProgressUpdated?.(this.id, currentTime, duration);
    }

    onItemCompleted?: (id: string) => void;
    onItemChanged?: (newItem: Audio) => void;

    player: AudioPlayer;

    next: Audio | null;
    prev: Audio | null;

    constructor(id: string, url: string, title: string) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.player = createAudioPlayer(url, {
            updateInterval: UPDATE_INTERVAL,
            
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
        this.player.setPlaybackRate(rate);
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
        console.log('🎵 Audio.pause() called for:', this.id);
        this.player.pause();
        console.log('🎵 Audio.pause() completed for:', this.id, 'playing:', this.player.playing);
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

    init() {
        console.log("Initializing PlayerManager");
        setAudioModeAsync({
            interruptionMode: "doNotMix",
            interruptionModeAndroid: "doNotMix",
            shouldPlayInBackground: true,
            shouldRouteThroughEarpiece: true,
            playsInSilentMode: true
        });
    }

    loadPlayList(playList: {
        id: string;
        title: string;
        url: string;
    }[], startWithId?: string) {
        this.clearAudio();

        this.audios = playList.map(item => new Audio(item.id, item.url, item.title));

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
                // console.log(`Progress updated for audio ${id}: ${currentTime} / ${duration}`);
                if (this.activeAudio !== audioItem)
                    return;
                this.setCurrentProgress?.(currentTime, duration);
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

        this.play();
    }

    clearAudio() {
        for (const audio of this.audios) {
            audio.destroy();
        }
        this.audios = [];
        this.activeAudio = null;

        console.log('Cleared audio:', this)
    }


    setRate(rate: number) {
        this.rate = rate;
        this.activeAudio?.setRate(rate);
    }

    play() {
        console.log('🎵 PlayerManager.play() called, activeAudio:', this.activeAudio?.id);
        this.activeAudio?.play(this.rate);
    }

    pause() {
        console.log('🎵 PlayerManager.pause() called, activeAudio:', this.activeAudio?.id);
        if (this.activeAudio) {
            this.activeAudio.pause();
            console.log('🎵 PlayerManager.pause() completed, activeAudio playing state:', this.activeAudio.player.playing);
        } else {
            console.log('⚠️ PlayerManager.pause() - No active audio to pause');
        }
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

    private changeItem(newItem: Audio) {
        this.setActiveAudio(newItem);
        this.activeAudio?.play(this.rate, true);
    }

    // Public methods to get current playback information
    getCurrentTime(): number {
        return this.activeAudio?.player.currentTime || 0;
    }

    getDuration(): number {
        return this.activeAudio?.player.duration || 1;
    }

    getCurrentAudioId(): string | null {
        return this.activeAudio?.id || null;
    }

}
