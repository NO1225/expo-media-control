package expo.modules.mediacontrolcar

import org.json.JSONArray
import org.json.JSONObject

/** An item of the car library, as sent from JavaScript (see `NativeCarMediaItem` in TypeScript) */
data class CarLibraryItem(
  val id: String,
  val title: String,
  val subtitle: String? = null,
  val artworkUri: String? = null,
  val playable: Boolean,
  val browsable: Boolean,
  /** null: loaded on demand with the JavaScript children loader */
  val children: List<CarLibraryItem>? = null,
  val style: String? = null,
  val durationSeconds: Double? = null,
  val explicit: Boolean = false
) {
  fun toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    subtitle?.let { put("subtitle", it) }
    artworkUri?.let { put("artworkUri", it) }
    put("playable", playable)
    put("browsable", browsable)
    children?.let { put("children", itemsToJson(it)) }
    style?.let { put("style", it) }
    durationSeconds?.let { put("duration", it) }
    if (explicit) put("explicit", true)
  }

  companion object {
    /** Parses an item received from JavaScript. Returns null for malformed input. */
    fun fromMap(map: Map<*, *>): CarLibraryItem? {
      val id = map["id"] as? String ?: return null
      if (id.isEmpty()) return null
      val children = (map["children"] as? List<*>)?.let { fromList(it) }
      return CarLibraryItem(
        id = id,
        title = map["title"] as? String ?: "",
        subtitle = map["subtitle"] as? String,
        artworkUri = (map["artworkUri"] as? String)?.takeIf { it.isNotEmpty() },
        playable = map["playable"] as? Boolean ?: false,
        browsable = map["browsable"] as? Boolean ?: false,
        children = children,
        style = map["style"] as? String,
        durationSeconds = (map["duration"] as? Number)?.toDouble(),
        explicit = map["explicit"] as? Boolean ?: false
      )
    }

    fun fromList(list: List<*>): List<CarLibraryItem> =
      list.mapNotNull { (it as? Map<*, *>)?.let(::fromMap) }

    fun fromJson(json: JSONObject): CarLibraryItem? {
      val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
      return CarLibraryItem(
        id = id,
        title = json.optString("title"),
        subtitle = json.optStringOrNull("subtitle"),
        artworkUri = json.optStringOrNull("artworkUri"),
        playable = json.optBoolean("playable"),
        browsable = json.optBoolean("browsable"),
        children = json.optJSONArray("children")?.let(::itemsFromJson),
        style = json.optStringOrNull("style"),
        durationSeconds = if (json.has("duration")) json.optDouble("duration") else null,
        explicit = json.optBoolean("explicit")
      )
    }

    fun itemsToJson(items: List<CarLibraryItem>): JSONArray =
      JSONArray().apply { items.forEach { put(it.toJson()) } }

    fun itemsFromJson(array: JSONArray): List<CarLibraryItem> =
      (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::fromJson) }

    private fun JSONObject.optStringOrNull(key: String): String? =
      if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
  }
}

/** The whole library: the top-level entries (tabs) and how they are displayed */
data class CarLibraryTree(
  val tabs: List<CarLibraryItem>,
  val style: String? = null
) {
  fun toJson(): JSONObject = JSONObject().apply {
    put("tabs", CarLibraryItem.itemsToJson(tabs))
    style?.let { put("style", it) }
  }

  /** Every item in the tree, depth first */
  fun allItems(): Sequence<CarLibraryItem> = sequence {
    val stack = ArrayDeque(tabs.reversed())
    while (stack.isNotEmpty()) {
      val item = stack.removeLast()
      yield(item)
      item.children?.reversed()?.forEach { stack.addLast(it) }
    }
  }

  companion object {
    fun fromMap(map: Map<*, *>): CarLibraryTree = CarLibraryTree(
      tabs = (map["tabs"] as? List<*>)?.let { CarLibraryItem.fromList(it) } ?: emptyList(),
      style = map["style"] as? String
    )

    fun fromJson(json: JSONObject): CarLibraryTree = CarLibraryTree(
      tabs = json.optJSONArray("tabs")?.let(CarLibraryItem::itemsFromJson) ?: emptyList(),
      style = if (json.has("style")) json.optString("style") else null
    )
  }
}
