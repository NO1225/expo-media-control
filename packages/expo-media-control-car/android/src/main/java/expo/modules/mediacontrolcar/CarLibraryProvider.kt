package expo.modules.mediacontrolcar

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import expo.modules.mediacontrol.MediaControlCenter
import expo.modules.mediacontrol.MediaLibraryProvider
import expo.modules.mediacontrol.MediaPlayRequest

/**
 * Answers Android Auto (and other media browsers) from [CarLibraryStore] and forwards play
 * requests to JavaScript.
 *
 * Created by expo-media-control's service through the manifest meta-data, or by
 * [ExpoMediaControlCarModule], whichever comes first.
 */
@OptIn(UnstableApi::class)
class CarLibraryProvider(context: Context) : MediaLibraryProvider {
  private val appContext = context.applicationContext
  private val artworkAuthority = "${appContext.packageName}.expomediacontrolcar.artwork"

  init {
    CarLibraryStore.initialize(appContext)
    CarLibraryStore.changeListener = { parentIds -> notifyChildrenChanged(parentIds) }
  }

  // =============================================
  // BROWSING
  // =============================================

  override fun onGetLibraryRoot(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    params: LibraryParams?
  ): ListenableFuture<LibraryResult<MediaItem>> {
    if (params?.isRecent == true) {
      // Playback resumption from the system UI isn't supported: the app starts playback itself
      return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
    }
    val extras = Bundle()
    styleValue(CarLibraryStore.library?.style)?.let {
      extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it)
      extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, it)
    }
    val rootParams = LibraryParams.Builder().setExtras(extras).build()
    return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), rootParams))
  }

  override fun onGetItem(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    mediaId: String
  ): ListenableFuture<LibraryResult<MediaItem>> {
    val item = when (mediaId) {
      ROOT_ID -> rootItem()
      EMPTY_ID -> emptyLibraryItem()
      else -> CarLibraryStore.findItem(mediaId)?.let(::toMediaItem)
    }
    return Futures.immediateFuture(
      if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    )
  }

  override fun onGetChildren(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    parentId: String,
    page: Int,
    pageSize: Int,
    params: LibraryParams?
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
    if (parentId == ROOT_ID) {
      val tabs = CarLibraryStore.library?.tabs
      val items = if (tabs.isNullOrEmpty()) listOf(emptyLibraryItem()) else tabs.map(::toMediaItem)
      return immediatePage(items, page, pageSize)
    }
    if (parentId == EMPTY_ID) {
      return immediatePage(emptyList(), page, pageSize)
    }

    val parent = CarLibraryStore.findItem(parentId)
    val cached = CarLibraryStore.cachedChildren(parentId)
    if (parent?.children != null) {
      return immediatePage(parent.children.map(::toMediaItem), page, pageSize)
    }
    if (!CarLibraryStore.canLoadChildren) {
      return if (cached != null || parent != null) {
        immediatePage(cached.orEmpty().map(::toMediaItem), page, pageSize)
      } else {
        Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
      }
    }

    // Loaded folders: answer from the cache first and refresh in the background. A change
    // triggers notifyChildrenChanged, which makes the car ask again.
    val load = CarLibraryStore.loadChildren(parentId)
    if (cached != null) {
      return immediatePage(cached.map(::toMediaItem), page, pageSize)
    }
    return Futures.transform(
      load,
      { items -> pageResult(items.orEmpty().map(::toMediaItem), page, pageSize) },
      MoreExecutors.directExecutor()
    )
  }

  // =============================================
  // SEARCH
  // =============================================

  override fun onSearch(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    query: String,
    params: LibraryParams?
  ): ListenableFuture<LibraryResult<Void>> {
    if (!CarLibraryStore.canSearch) {
      session.notifySearchResultChanged(browser, query, 0, params)
      return Futures.immediateFuture(LibraryResult.ofVoid())
    }
    val search = CarLibraryStore.search(query)
    search.addListener({
      val count = search.get()?.size ?: 0
      session.notifySearchResultChanged(browser, query, count, params)
    }, MoreExecutors.directExecutor())
    return Futures.immediateFuture(LibraryResult.ofVoid())
  }

  override fun onGetSearchResult(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    query: String,
    page: Int,
    pageSize: Int,
    params: LibraryParams?
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
    CarLibraryStore.cachedSearchResults(query)?.let {
      return immediatePage(it.map(::toMediaItem), page, pageSize)
    }
    val inFlight = CarLibraryStore.searchInFlight(query)
      ?: return immediatePage(emptyList(), page, pageSize)
    return Futures.transform(
      inFlight,
      { items -> pageResult(items.orEmpty().map(::toMediaItem), page, pageSize) },
      MoreExecutors.directExecutor()
    )
  }

  // =============================================
  // PLAYBACK
  // =============================================

  override fun onResolveMediaItems(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>
  ): ListenableFuture<List<MediaItem>> {
    val resolved = mediaItems.map { requested ->
      val item = CarLibraryStore.findItem(requested.mediaId) ?: return@map requested
      toMediaItem(item).buildUpon().setRequestMetadata(requested.requestMetadata).build()
    }
    return Futures.immediateFuture(resolved)
  }

  override fun onPlayRequest(request: MediaPlayRequest) {
    val mediaId = request.mediaItem.mediaId
    val payload = mutableMapOf<String, Any?>("playWhenReady" to request.playWhenReady)
    if (mediaId.isNotEmpty() && mediaId != ROOT_ID && mediaId != EMPTY_ID) {
      payload["itemId"] = mediaId
    }
    request.mediaItem.requestMetadata.searchQuery?.let { payload["query"] = it }
    if (!payload.containsKey("itemId") && !payload.containsKey("query")) {
      // "Play something" without a query or an item
      payload["query"] = ""
    }
    if (request.startPositionMs > 0) {
      payload["position"] = request.startPositionMs / 1000.0
    }

    val bridge = CarLibraryStore.bridge
    if (bridge != null) {
      bridge.deliverPlayRequest(payload)
    } else {
      CarLibraryStore.pendingPlayRequest = payload
    }
  }

  // =============================================
  // HELPERS
  // =============================================

  private fun notifyChildrenChanged(parentIds: Collection<String>) {
    val session = MediaControlCenter.session ?: return
    for (parentId in parentIds) {
      val count = when (parentId) {
        ROOT_ID -> CarLibraryStore.library?.tabs?.size?.takeIf { it > 0 } ?: 1
        else -> CarLibraryStore.cachedChildren(parentId)?.size ?: Int.MAX_VALUE
      }
      session.notifyChildrenChanged(parentId, count, null)
    }
  }

  private fun rootItem(): MediaItem = MediaItem.Builder()
    .setMediaId(ROOT_ID)
    .setMediaMetadata(
      MediaMetadata.Builder()
        .setTitle(appContext.getString(R.string.expo_media_control_car_root_title))
        .setIsBrowsable(true)
        .setIsPlayable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        .build()
    )
    .build()

  private fun emptyLibraryItem(): MediaItem = MediaItem.Builder()
    .setMediaId(EMPTY_ID)
    .setMediaMetadata(
      MediaMetadata.Builder()
        .setTitle(appContext.getString(R.string.expo_media_control_car_empty_library))
        .setIsBrowsable(true)
        .setIsPlayable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        .build()
    )
    .build()

  private fun toMediaItem(item: CarLibraryItem): MediaItem {
    val extras = Bundle()
    styleValue(item.style)?.let {
      extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it)
      extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, it)
    }
    if (item.explicit) {
      extras.putLong(MediaConstants.EXTRAS_KEY_IS_EXPLICIT, 1)
    }

    val metadata = MediaMetadata.Builder()
      .setTitle(item.title)
      .setDisplayTitle(item.title)
      .setIsBrowsable(item.browsable)
      .setIsPlayable(item.playable)
      .setMediaType(if (item.browsable) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED else MediaMetadata.MEDIA_TYPE_MUSIC)
      .setExtras(extras)
    item.subtitle?.let {
      metadata.setSubtitle(it)
      metadata.setArtist(it)
    }
    item.durationSeconds?.let { metadata.setDurationMs((it * 1000).toLong()) }
    item.artworkUri?.let { metadata.setArtworkUri(carArtworkUri(it)) }

    return MediaItem.Builder()
      .setMediaId(item.id)
      .setMediaMetadata(metadata.build())
      .build()
  }

  /** Android Auto only loads content:// and android.resource:// artwork, so serve the rest */
  private fun carArtworkUri(uri: String): Uri {
    val parsed = Uri.parse(uri)
    return when (parsed.scheme) {
      "content", "android.resource" -> parsed
      else -> CarArtworkProvider.buildUri(artworkAuthority, uri)
    }
  }

  private fun styleValue(style: String?): Int? = when (style) {
    "list" -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
    "grid" -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
    else -> null
  }

  private fun immediatePage(
    items: List<MediaItem>,
    page: Int,
    pageSize: Int
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
    Futures.immediateFuture(pageResult(items, page, pageSize))

  private fun pageResult(items: List<MediaItem>, page: Int, pageSize: Int): LibraryResult<ImmutableList<MediaItem>> {
    val from = page.toLong() * pageSize
    if (from >= items.size) {
      return LibraryResult.ofItemList(ImmutableList.of(), null)
    }
    val to = minOf(items.size.toLong(), from + pageSize).toInt()
    return LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(from.toInt(), to)), null)
  }

  companion object {
    const val ROOT_ID = "__expo_media_control_car_root__"
    const val EMPTY_ID = "__expo_media_control_car_empty__"
  }
}
