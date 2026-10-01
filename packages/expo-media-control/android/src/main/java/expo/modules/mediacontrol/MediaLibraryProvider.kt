package expo.modules.mediacontrol

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Supplies a browsable media library to [MediaPlaybackService], for Android Auto, Wear OS and
 * other media browsers. Without a provider the service exposes no library, like in 2.0.
 *
 * Another package (such as expo-media-control-car) registers a provider in one of two ways:
 * - In its Android manifest, with an application meta-data entry named
 *   [MediaControlCenter.LIBRARY_PROVIDER_META_DATA] whose value is the provider's class name. The
 *   class needs a public constructor taking a [android.content.Context]. The service creates it
 *   when it starts, so the library is available even when JavaScript is not running.
 * - At runtime, by setting [MediaControlCenter.libraryProvider].
 *
 * All methods are called on the main thread.
 */
@OptIn(UnstableApi::class)
interface MediaLibraryProvider {
  fun onGetLibraryRoot(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    params: LibraryParams?
  ): ListenableFuture<LibraryResult<MediaItem>>

  fun onGetItem(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    mediaId: String
  ): ListenableFuture<LibraryResult<MediaItem>>

  fun onGetChildren(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    parentId: String,
    page: Int,
    pageSize: Int,
    params: LibraryParams?
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>

  /**
   * Starts a search. Report the result count with [MediaLibrarySession.notifySearchResultChanged]
   * once it is known; the browser then calls [onGetSearchResult].
   */
  fun onSearch(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    query: String,
    params: LibraryParams?
  ): ListenableFuture<LibraryResult<Void>> =
    Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))

  fun onGetSearchResult(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    query: String,
    page: Int,
    pageSize: Int,
    params: LibraryParams?
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
    Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))

  /**
   * Resolves the items a controller asked to play. They usually only carry a media id, or a
   * search query in [MediaItem.requestMetadata] for voice requests ("play X"). Return the items
   * with their metadata filled in; they don't need a URI because the app plays them itself.
   */
  fun onResolveMediaItems(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>
  ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(mediaItems)

  /** A controller asked to play an item. Called after [onResolveMediaItems]. */
  fun onPlayRequest(request: MediaPlayRequest)
}

/**
 * A request from a controller (Android Auto, Assistant, ...) to play [mediaItem].
 *
 * @property mediaItem The resolved item. For a voice request without an exact match,
 *   `mediaItem.requestMetadata.searchQuery` holds the spoken query.
 * @property startPositionMs The requested start position, or 0.
 * @property playWhenReady Whether the controller also asked to start playback (false for
 *   "prepare" requests).
 */
data class MediaPlayRequest(
  val mediaItem: MediaItem,
  val startPositionMs: Long,
  val playWhenReady: Boolean
)
