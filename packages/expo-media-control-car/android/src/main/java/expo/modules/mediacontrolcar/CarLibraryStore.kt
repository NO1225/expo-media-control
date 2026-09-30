package expo.modules.mediacontrolcar

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Connection to the JavaScript side, set by [ExpoMediaControlCarModule] while the runtime is up.
 */
interface CarJsBridge {
  fun requestChildren(requestId: String, parentId: String)
  fun requestSearch(requestId: String, query: String)
  fun deliverPlayRequest(request: Map<String, Any?>)
}

/**
 * The native copy of the car library, shared by the library provider (answering the car) and
 * the Expo module (receiving updates from JavaScript).
 *
 * - The tree from `setLibrary()` is kept in memory and saved to disk, so the car can browse it
 *   right after the phone connects, even before JavaScript runs.
 * - Folders loaded with the JavaScript children loader are cached in memory and refreshed in the
 *   background when opened again.
 *
 * Must be used on the main thread, except [initialize] and [isKnownArtwork].
 */
object CarLibraryStore {
  private const val TAG = "ExpoMediaControlCar"
  private const val LIBRARY_FILE = "expo-media-control-car/library.json"
  private const val MAX_LOADED_FOLDERS = 100
  private const val MAX_SEARCHES = 10

  /** How long the car waits for JavaScript before it gets the cached or an empty result */
  const val JS_TIMEOUT_MS = 8_000L

  var bridge: CarJsBridge? = null
  var hasChildrenLoader = false
  var hasSearchHandler = false

  /** Called with the ids of folders whose children changed */
  var changeListener: ((Collection<String>) -> Unit)? = null

  var library: CarLibraryTree? = null
    private set

  private var appContext: Context? = null
  private val handler = Handler(Looper.getMainLooper())
  private val diskExecutor = Executors.newSingleThreadExecutor()

  private val treeItems = HashMap<String, CarLibraryItem>()
  private val loadedChildren = lruMap<String, List<CarLibraryItem>>(MAX_LOADED_FOLDERS)
  private val loadedItems = lruMap<String, CarLibraryItem>(MAX_LOADED_FOLDERS * 50)
  private val searchResults = lruMap<String, List<CarLibraryItem>>(MAX_SEARCHES)

  private val pendingChildren = HashMap<String, SettableFuture<List<CarLibraryItem>?>>()
  private val pendingSearches = HashMap<String, SettableFuture<List<CarLibraryItem>?>>()
  private val childrenInFlight = HashMap<String, ListenableFuture<List<CarLibraryItem>?>>()
  private val searchesInFlight = HashMap<String, ListenableFuture<List<CarLibraryItem>?>>()

  /** Artwork URIs of known items; the artwork provider only serves these */
  private val knownArtwork: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** A play request that arrived while no JavaScript listener was registered */
  var pendingPlayRequest: Map<String, Any?>? = null

  /** Loads the saved library once. Safe to call from any thread. */
  @Synchronized
  fun initialize(context: Context) {
    if (appContext != null) return
    val applicationContext = context.applicationContext
    appContext = applicationContext
    try {
      val file = File(applicationContext.filesDir, LIBRARY_FILE)
      if (file.exists()) {
        val tree = CarLibraryTree.fromJson(JSONObject(file.readText()))
        if (Looper.myLooper() == Looper.getMainLooper()) {
          applyLibrary(tree)
        } else {
          tree.allItems().forEach { item -> item.artworkUri?.let { knownArtwork.add(it) } }
          handler.post { if (library == null) applyLibrary(tree) }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't read the saved car library", e)
    }
  }

  fun setLibrary(tree: CarLibraryTree) {
    val previousFolders = library?.allItems()?.filter { it.browsable }?.map { it.id }?.toSet() ?: emptySet()
    applyLibrary(tree)
    save(tree)
    val folders = tree.allItems().filter { it.browsable }.map { it.id }.toSet()
    changeListener?.invoke(setOf(CarLibraryProvider.ROOT_ID) + previousFolders + folders)
  }

  private fun applyLibrary(tree: CarLibraryTree) {
    library = tree
    treeItems.clear()
    tree.allItems().forEach { item ->
      treeItems[item.id] = item
      item.artworkUri?.let { knownArtwork.add(it) }
    }
  }

  private fun save(tree: CarLibraryTree) {
    val context = appContext ?: return
    val json = tree.toJson().toString()
    diskExecutor.execute {
      try {
        val file = File(context.filesDir, LIBRARY_FILE)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json)
        if (!temp.renameTo(file)) {
          file.writeText(json)
          temp.delete()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Couldn't save the car library", e)
      }
    }
  }

  fun findItem(id: String): CarLibraryItem? = treeItems[id] ?: loadedItems[id]

  fun isKnownArtwork(uri: String): Boolean = knownArtwork.contains(uri)

  /** Children given in the tree, or the cached result of the children loader */
  fun cachedChildren(parentId: String): List<CarLibraryItem>? =
    treeItems[parentId]?.children ?: loadedChildren[parentId]

  val canLoadChildren: Boolean
    get() = bridge != null && hasChildrenLoader

  val canSearch: Boolean
    get() = bridge != null && hasSearchHandler

  // Futures are completed on the main thread (by JavaScript answers or timeouts), so their
  // listeners run there too.

  /**
   * Asks JavaScript for the children of [parentId]. Completes with null when JavaScript fails or
   * doesn't answer in time. Concurrent requests for the same folder share one JavaScript call.
   */
  fun loadChildren(parentId: String): ListenableFuture<List<CarLibraryItem>?> {
    childrenInFlight[parentId]?.let { return it }
    val future = request(pendingChildren) { requestId -> bridge?.requestChildren(requestId, parentId) }
    childrenInFlight[parentId] = future
    future.addListener({
      childrenInFlight.remove(parentId)
      val items = future.get() ?: return@addListener
      val changed = loadedChildren[parentId] != items
      rememberLoaded(items)
      loadedChildren[parentId] = items
      if (changed) changeListener?.invoke(listOf(parentId))
    }, MoreExecutors.directExecutor())
    return future
  }

  fun search(query: String): ListenableFuture<List<CarLibraryItem>?> {
    searchesInFlight[query]?.let { return it }
    val future = request(pendingSearches) { requestId -> bridge?.requestSearch(requestId, query) }
    searchesInFlight[query] = future
    future.addListener({
      searchesInFlight.remove(query)
      val items = future.get() ?: emptyList()
      rememberLoaded(items)
      searchResults[query] = items
    }, MoreExecutors.directExecutor())
    return future
  }

  fun searchInFlight(query: String): ListenableFuture<List<CarLibraryItem>?>? = searchesInFlight[query]

  fun cachedSearchResults(query: String): List<CarLibraryItem>? = searchResults[query]

  fun resolveChildren(requestId: String, items: List<CarLibraryItem>?) {
    pendingChildren.remove(requestId)?.set(items)
  }

  fun resolveSearch(requestId: String, items: List<CarLibraryItem>?) {
    pendingSearches.remove(requestId)?.set(items)
  }

  /** Drops the cached children of a loaded folder and tells the car to reload it */
  fun invalidateChildren(parentId: String) {
    loadedChildren.remove(parentId)
    changeListener?.invoke(listOf(parentId))
  }

  /** Fails every open request, for when JavaScript goes away */
  fun cancelPendingRequests() {
    pendingChildren.values.forEach { it.set(null) }
    pendingChildren.clear()
    pendingSearches.values.forEach { it.set(null) }
    pendingSearches.clear()
  }

  private fun request(
    pending: HashMap<String, SettableFuture<List<CarLibraryItem>?>>,
    send: (String) -> Unit
  ): ListenableFuture<List<CarLibraryItem>?> {
    val requestId = UUID.randomUUID().toString()
    val future = SettableFuture.create<List<CarLibraryItem>?>()
    pending[requestId] = future
    handler.postDelayed({ pending.remove(requestId)?.set(null) }, JS_TIMEOUT_MS)
    send(requestId)
    return future
  }

  private fun rememberLoaded(items: List<CarLibraryItem>) {
    for (item in items) {
      loadedItems[item.id] = item
      item.artworkUri?.let { knownArtwork.add(it) }
      item.children?.let { rememberLoaded(it) }
    }
  }

  private fun <K, V> lruMap(maxSize: Int): MutableMap<K, V> =
    Collections.synchronizedMap(object : LinkedHashMap<K, V>(16, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    })
}
