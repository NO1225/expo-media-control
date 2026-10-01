import { CarLibrary, type CarMediaItem } from 'expo-media-control-car';

/**
 * The example app uses expo-media-control-car unless EXPO_PUBLIC_MEDIA_CONTROL_CAR=0.
 * app.config.js reads the same variable to add or leave out the car config plugin.
 */
export const CAR_ENABLED = process.env.EXPO_PUBLIC_MEDIA_CONTROL_CAR !== '0';

export interface CarTrack {
  id: string;
  title: string;
  artist: string;
  album: string;
  artWork: string;
}

const albumId = (album: string) => `album:${album}`;

function toItem(track: CarTrack): CarMediaItem {
  return {
    id: track.id,
    title: track.title,
    subtitle: track.artist,
    artwork: track.artWork,
  };
}

/**
 * Shows the sample tracks in Android Auto and CarPlay:
 * - "Tracks" is pushed with setLibrary()
 * - "Albums" is browsable without children, so its content is loaded on demand
 * - Search and voice requests match titles, artists and albums
 *
 * Returns a cleanup function.
 */
export function setupCarLibrary(
  tracks: CarTrack[],
  playTrack: (trackId: string) => void,
): () => void {
  const albums = [...new Set(tracks.map((track) => track.album))];
  const search = (query: string) => {
    const text = query.trim().toLowerCase();
    return tracks.filter((track) =>
      [track.title, track.artist, track.album].some((value) =>
        value.toLowerCase().includes(text),
      ),
    );
  };

  CarLibrary.setLibrary({
    tabs: [
      { id: 'tracks', title: 'Tracks', children: tracks.map(toItem) },
      { id: 'albums', title: 'Albums', browsable: true, style: 'grid' },
    ],
  }).catch((error) => console.warn('Setting the car library failed', error));

  CarLibrary.setChildrenLoader(async (parentId) => {
    if (parentId === 'albums') {
      return albums.map((album) => ({
        id: albumId(album),
        title: album,
        subtitle: tracks.find((track) => track.album === album)?.artist,
        browsable: true,
      }));
    }
    const album = albums.find((name) => albumId(name) === parentId);
    return album ? tracks.filter((track) => track.album === album).map(toItem) : [];
  });

  CarLibrary.setSearchHandler((query) => search(query).map(toItem));

  const subscription = CarLibrary.addPlayRequestListener(({ itemId, query }) => {
    console.log('🚗 Car play request', { itemId, query });
    const track = tracks.find((t) => t.id === itemId) ?? search(query ?? '')[0] ?? tracks[0];
    if (track) {
      playTrack(track.id);
    }
  });

  return () => {
    subscription.remove();
    CarLibrary.setChildrenLoader(null);
    CarLibrary.setSearchHandler(null);
  };
}
