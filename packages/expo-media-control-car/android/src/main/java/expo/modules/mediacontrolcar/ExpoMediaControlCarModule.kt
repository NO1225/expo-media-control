package expo.modules.mediacontrolcar

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.mediacontrol.MediaControlCenter

/**
 * Receives the car library from JavaScript and sends the car's requests (children to load,
 * searches, play requests) back as events.
 */
@OptIn(UnstableApi::class)
class ExpoMediaControlCarModule : Module() {

  private val bridge = object : CarJsBridge {
    override fun requestChildren(requestId: String, parentId: String) {
      sendEvent(EVENT_LOAD_CHILDREN, mapOf("requestId" to requestId, "parentId" to parentId))
    }

    override fun requestSearch(requestId: String, query: String) {
      sendEvent(EVENT_SEARCH, mapOf("requestId" to requestId, "query" to query))
    }

    override fun deliverPlayRequest(request: Map<String, Any?>) {
      if (hasPlayRequestListener) {
        sendEvent(EVENT_PLAY_REQUEST, request)
      } else {
        CarLibraryStore.pendingPlayRequest = request
      }
    }
  }

  private var hasPlayRequestListener = false

  override fun definition() = ModuleDefinition {
    Name("ExpoMediaControlCar")

    Events(EVENT_LOAD_CHILDREN, EVENT_SEARCH, EVENT_PLAY_REQUEST)

    OnDestroy {
      runOnMain { detach() }
    }

    AsyncFunction("setLibrary") { library: Map<String, Any?> ->
      attach()
      CarLibraryStore.setLibrary(CarLibraryTree.fromMap(library))
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("setHandlers") { handlers: Map<String, Any?> ->
      attach()
      CarLibraryStore.hasChildrenLoader = handlers["childrenLoader"] as? Boolean ?: false
      CarLibraryStore.hasSearchHandler = handlers["search"] as? Boolean ?: false
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("resolveChildren") { requestId: String, items: List<Map<String, Any?>>? ->
      CarLibraryStore.resolveChildren(requestId, items?.let { CarLibraryItem.fromList(it) })
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("resolveSearch") { requestId: String, items: List<Map<String, Any?>>? ->
      CarLibraryStore.resolveSearch(requestId, items?.let { CarLibraryItem.fromList(it) })
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("notifyChildrenChanged") { parentId: String ->
      CarLibraryStore.invalidateChildren(parentId)
    }.runOnQueue(Queues.MAIN)

    /** Called by JavaScript after adding a play request listener */
    AsyncFunction("flushPendingPlayRequest") { ->
      attach()
      hasPlayRequestListener = true
      CarLibraryStore.pendingPlayRequest?.let { request ->
        CarLibraryStore.pendingPlayRequest = null
        sendEvent(EVENT_PLAY_REQUEST, request)
      }
    }.runOnQueue(Queues.MAIN)
  }

  private fun attach() {
    val context = appContext.reactContext ?: throw ReactContextLostException()
    CarLibraryStore.initialize(context)
    CarLibraryStore.bridge = bridge
    // Registers the provider now if the service hasn't started yet, so play requests reach it
    if (MediaControlCenter.getLibraryProvider(context) == null) {
      MediaControlCenter.libraryProvider = CarLibraryProvider(context)
    }
  }

  private fun detach() {
    if (CarLibraryStore.bridge === bridge) {
      CarLibraryStore.bridge = null
      CarLibraryStore.hasChildrenLoader = false
      CarLibraryStore.hasSearchHandler = false
      CarLibraryStore.cancelPendingRequests()
    }
  }

  private fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      block()
    } else {
      Handler(Looper.getMainLooper()).post(block)
    }
  }

  companion object {
    private const val EVENT_LOAD_CHILDREN = "onLoadChildren"
    private const val EVENT_SEARCH = "onSearch"
    private const val EVENT_PLAY_REQUEST = "onPlayRequest"
  }
}

private class ReactContextLostException :
  CodedException("CONTEXT_LOST", "React context is not available", null)
