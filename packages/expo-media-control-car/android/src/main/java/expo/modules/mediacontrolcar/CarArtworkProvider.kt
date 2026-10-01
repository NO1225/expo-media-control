package expo.modules.mediacontrolcar

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Serves browse artwork to Android Auto, which only loads `content://` URIs.
 *
 * `content://<package>.expomediacontrolcar.artwork/image?uri=<original uri>` returns the image at
 * the original `http(s)://` or `file://` URI, downloaded into the app's cache. Only URIs of items
 * in the current library are served, so other apps can't use this provider to fetch arbitrary
 * URLs or read arbitrary files.
 */
class CarArtworkProvider : ContentProvider() {

  override fun onCreate(): Boolean = true

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    if (mode != "r") throw SecurityException("Artwork is read-only")
    val context = context ?: throw FileNotFoundException("No context")
    val original = uri.getQueryParameter(QUERY_URI) ?: throw FileNotFoundException("Missing artwork URI")

    CarLibraryStore.initialize(context)
    if (!CarLibraryStore.isKnownArtwork(original)) {
      throw FileNotFoundException("Unknown artwork")
    }

    val parsed = Uri.parse(original)
    val file = when (parsed.scheme) {
      "file" -> File(parsed.path ?: throw FileNotFoundException("Invalid file URI"))
      "http", "https" -> download(original, File(context.cacheDir, CACHE_DIR))
      else -> throw FileNotFoundException("Unsupported artwork URI")
    }
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  }

  override fun getType(uri: Uri): String = "image/*"

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?
  ): Cursor? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?
  ): Int = 0

  private fun download(url: String, cacheDir: File): File {
    val file = File(cacheDir, sha1(url))
    if (file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MS) {
      return file
    }
    cacheDir.mkdirs()
    val temp = File.createTempFile("download", ".tmp", cacheDir)
    try {
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.connectTimeout = TIMEOUT_MS
      connection.readTimeout = TIMEOUT_MS
      connection.instanceFollowRedirects = true
      try {
        if (connection.responseCode !in 200..299) {
          throw FileNotFoundException("HTTP ${connection.responseCode}")
        }
        connection.inputStream.use { input ->
          temp.outputStream().use { output ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
              val read = input.read(buffer)
              if (read < 0) break
              total += read
              if (total > MAX_BYTES) throw FileNotFoundException("Artwork too large")
              output.write(buffer, 0, read)
            }
          }
        }
      } finally {
        connection.disconnect()
      }
      if (!temp.renameTo(file)) {
        temp.copyTo(file, overwrite = true)
      }
      return file
    } catch (e: Exception) {
      Log.w(TAG, "Couldn't load car artwork", e)
      // A stale copy is better than no artwork
      if (file.exists()) return file
      throw FileNotFoundException("Couldn't load artwork")
    } finally {
      temp.delete()
    }
  }

  private fun sha1(value: String): String =
    MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

  companion object {
    private const val TAG = "ExpoMediaControlCar"
    private const val QUERY_URI = "uri"
    private const val CACHE_DIR = "expo-media-control-car-artwork"
    private const val CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 10L * 1024 * 1024

    fun buildUri(authority: String, original: String): Uri = Uri.Builder()
      .scheme("content")
      .authority(authority)
      .path("image")
      .appendQueryParameter(QUERY_URI, original)
      .build()
  }
}
