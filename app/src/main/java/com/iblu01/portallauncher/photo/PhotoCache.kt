package com.iblu01.portallauncher.photo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Metadata stored alongside a cached image. [cacheKey] is the stable asset identity and maps to an
 * opaque disk name. [createdAt] is used for deterministic oldest-entry eviction.
 */
data class CacheMeta(
    val cacheKey: String,
    val assetId: String,
    val provider: String,
    val createdAt: Long,
    val size: Long,
)

/**
 * Bounded disk cache for provider-agnostic photo frames. Implementations must be safe to call from
 * coroutines and must treat the cache directory as disposable.
 */
interface PhotoCache {
    /** Write [bytes] for the asset identified by [meta]. Overwrites an existing entry. */
    suspend fun put(meta: CacheMeta, bytes: ByteArray)

    /** Read a cached asset by its [cacheKey]. Returns null if absent or corrupt. */
    suspend fun get(cacheKey: String): Pair<ByteArray, CacheMeta>?

    /** Evict one asset by [cacheKey]. */
    suspend fun evict(cacheKey: String)

    /** All keys currently in the cache. */
    suspend fun keys(): Set<String>

    /** Metadata for all valid cache entries, used to reconstruct an offline playlist after restart. */
    suspend fun entries(): List<CacheMeta>

    /** Evict oldest entries until the cache fits the bounds. */
    suspend fun trimTo(maxEntries: Int, maxBytes: Long)

    /** Total cached bytes currently on disk. */
    suspend fun totalBytes(): Long

    /** Remove crash-orphaned image/temp files and stale manifest entries. */
    suspend fun sweepOrphans() = Unit

    /** The on-disk file that stores [cacheKey], whether it exists yet or not. */
    fun fileFor(cacheKey: String): File
}

/**
 * File-backed cache inside [context.filesDir]/photos. Stores one binary file per asset plus a single
 * JSON manifest for metadata. The manifest is rewritten on every write/evict; for v1 cache sizes
 * (a few hundred assets) this is acceptable and keeps the implementation Android-free in tests.
 */
class FilePhotoCache(context: Context) : PhotoCache {
    private val dir = File(context.filesDir, "photos").also { it.mkdirs() }
    private val manifestFile = File(dir, "manifest.json")
    private val lock = Any()

    override suspend fun put(meta: CacheMeta, bytes: ByteArray) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val file = entryFile(meta.cacheKey)
            writeAtomically(file, bytes)
            val manifest = readManifest().toMutableMap()
            manifest[meta.cacheKey] = meta.copy(size = bytes.size.toLong())
            writeManifest(manifest)
        }
    }

    override suspend fun get(cacheKey: String): Pair<ByteArray, CacheMeta>? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val manifest = readManifest()
            val meta = manifest[cacheKey] ?: return@synchronized null
            val file = entryFile(cacheKey)
            if (!file.exists()) {
                writeManifest(manifest - cacheKey)
                return@synchronized null
            }
            runCatching { file.readBytes() to meta }.getOrNull()
        }
    }

    override suspend fun evict(cacheKey: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val manifest = readManifest().toMutableMap()
            manifest.remove(cacheKey)
            writeManifest(manifest)
            entryFile(cacheKey).delete()
            Unit
        }
    }

    override suspend fun keys(): Set<String> = withContext(Dispatchers.IO) {
        synchronized(lock) { readManifest().keys }
    }

    override suspend fun entries(): List<CacheMeta> = withContext(Dispatchers.IO) {
        synchronized(lock) { readManifest().values.filter { entryFile(it.cacheKey).exists() } }
    }

    override suspend fun trimTo(maxEntries: Int, maxBytes: Long) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            var manifest = readManifest().toMutableMap()
            val entries = manifest.values.sortedBy { it.createdAt }
            var totalBytes = entries.sumOf { it.size }
            var evicted = 0
            for (entry in entries) {
                if (manifest.size <= maxEntries && totalBytes <= maxBytes) break
                manifest.remove(entry.cacheKey)
                entryFile(entry.cacheKey).delete()
                totalBytes -= entry.size
                evicted++
            }
            if (evicted > 0) writeManifest(manifest)
        }
    }

    override suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        synchronized(lock) { readManifest().values.sumOf { it.size } }
    }

    override suspend fun sweepOrphans() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val manifest = readManifest().toMutableMap()
            val validNames = manifest.keys.map(::cacheKeyToFileName).toSet()
            dir.listFiles().orEmpty().forEach { file ->
                if ((file.extension == "img" && file.name !in validNames) || file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
            val existing = manifest.filterValues { entryFile(it.cacheKey).exists() }
            if (existing.size != manifest.size) writeManifest(existing)
        }
    }

    /** Exposed for tests and reconciliation. */
    fun cacheDir(): File = dir

    override fun fileFor(cacheKey: String): File = entryFile(cacheKey)

    private fun readManifest(): Map<String, CacheMeta> = runCatching {
        if (!manifestFile.exists()) return emptyMap()
        val json = JSONObject(manifestFile.readText())
        json.keys().asSequence().mapNotNull { key ->
            val o = json.optJSONObject(key) ?: return@mapNotNull null
            CacheMeta(
                cacheKey = key,
                assetId = o.optString("assetId"),
                provider = o.optString("provider"),
                createdAt = o.optLong("createdAt"),
                size = o.optLong("size"),
            )
        }.associateBy { it.cacheKey }
    }.getOrDefault(emptyMap())

    private fun writeManifest(manifest: Map<String, CacheMeta>) {
        val json = JSONObject()
        manifest.forEach { (key, meta) ->
            json.put(key, JSONObject().apply {
                put("assetId", meta.assetId)
                put("provider", meta.provider)
                put("createdAt", meta.createdAt)
                put("size", meta.size)
            })
        }
        writeAtomically(manifestFile, json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun entryFile(cacheKey: String): File = File(dir, cacheKeyToFileName(cacheKey))

    companion object {
        /** Opaque, filesystem-safe name derived from a stable cache key. */
        fun cacheKeyToFileName(cacheKey: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(cacheKey.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) } + ".img"
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeBytes(bytes)
        try {
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }
}
