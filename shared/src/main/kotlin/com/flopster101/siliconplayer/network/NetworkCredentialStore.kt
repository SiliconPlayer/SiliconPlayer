package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale

private const val NETWORK_CREDENTIALS_STORE_VERSION = 1
private const val NETWORK_CREDENTIALS_FILE_NAME = "credentials.json"
private const val NETWORK_CREDENTIALS_HTTP_ROOT_SCOPE = "/"

internal fun networkCredentialsFile(configDir: File): File =
    File(configDir, NETWORK_CREDENTIALS_FILE_NAME)

private data class StoredNetworkCredential(
    val username: String?,
    val password: String?
)

internal object NetworkCredentialStore {
    private val lock = Any()
    private var loaded = false
    private val smbCredentials = LinkedHashMap<String, StoredNetworkCredential>()
    private val httpCredentials = LinkedHashMap<String, LinkedHashMap<String, StoredNetworkCredential>>()

    fun remember(spec: SmbSourceSpec, username: String? = spec.username, password: String? = spec.password) {
        val normalizedUsername = username?.trim().takeUnless { it.isNullOrBlank() }
        val normalizedPassword = password?.trim().takeUnless { it.isNullOrBlank() }
        val exactKey = smbExactKey(spec)
        val hostKey = smbHostKey(spec)
        synchronized(lock) {
            ensureLoadedLocked()
            if (normalizedUsername == null && normalizedPassword == null) {
                smbCredentials.remove(exactKey)
                smbCredentials.remove(hostKey)
            } else {
                val stored = StoredNetworkCredential(normalizedUsername, normalizedPassword)
                smbCredentials[exactKey] = stored
                smbCredentials[hostKey] = stored
            }
            persistLocked()
        }
    }

    fun remember(spec: HttpSourceSpec, username: String? = spec.username, password: String? = spec.password) {
        val normalizedUsername = username?.trim().takeUnless { it.isNullOrBlank() }
        val normalizedPassword = password?.trim().takeUnless { it.isNullOrBlank() }
        val authorityKey = httpAuthorityKey(spec)
        val exactScope = httpDirectoryScope(spec)
        synchronized(lock) {
            ensureLoadedLocked()
            val scopedMap = httpCredentials.getOrPut(authorityKey) { LinkedHashMap() }
            if (normalizedUsername == null && normalizedPassword == null) {
                scopedMap.remove(exactScope)
                scopedMap.remove(NETWORK_CREDENTIALS_HTTP_ROOT_SCOPE)
                if (scopedMap.isEmpty()) {
                    httpCredentials.remove(authorityKey)
                }
            } else {
                val stored = StoredNetworkCredential(normalizedUsername, normalizedPassword)
                scopedMap[exactScope] = stored
                scopedMap[NETWORK_CREDENTIALS_HTTP_ROOT_SCOPE] = stored
            }
            persistLocked()
        }
    }

    fun applyTo(spec: SmbSourceSpec): SmbSourceSpec {
        val stored = synchronized(lock) {
            ensureLoadedLocked()
            smbCredentials[smbExactKey(spec)] ?: smbCredentials[smbHostKey(spec)]
        }
        val merged = mergeCredentials(
            existingUsername = spec.username,
            existingPassword = spec.password,
            stored = stored
        )
        return spec.copy(
            username = merged.first,
            password = merged.second
        )
    }

    fun applyTo(spec: HttpSourceSpec): HttpSourceSpec {
        val authorityKey = httpAuthorityKey(spec)
        val targetScope = httpDirectoryScope(spec)
        val stored = synchronized(lock) {
            ensureLoadedLocked()
            httpCredentials[authorityKey]
                ?.entries
                ?.asSequence()
                ?.filter { (pathScope, _) -> httpScopeMatches(targetScope, pathScope) }
                ?.maxByOrNull { (pathScope, _) -> pathScope.length }
                ?.value
        }
        val merged = mergeCredentials(
            existingUsername = spec.username,
            existingPassword = spec.password,
            stored = stored
        )
        return spec.copy(
            username = merged.first,
            password = merged.second
        )
    }

    fun resolvedCredentialPair(spec: SmbSourceSpec): Pair<String?, String?>? {
        val resolved = applyTo(spec)
        return credentialPair(resolved.username, resolved.password)
    }

    fun resolvedCredentialPair(spec: HttpSourceSpec): Pair<String?, String?>? {
        val resolved = applyTo(spec)
        return credentialPair(resolved.username, resolved.password)
    }

    private fun credentialPair(username: String?, password: String?): Pair<String?, String?>? {
        val normalizedUsername = username?.trim().takeUnless { it.isNullOrBlank() }
        val normalizedPassword = password?.trim().takeUnless { it.isNullOrBlank() }
        if (normalizedUsername == null && normalizedPassword == null) return null
        return normalizedUsername to normalizedPassword
    }

    fun clearAll() {
        synchronized(lock) {
            ensureLoadedLocked()
            smbCredentials.clear()
            httpCredentials.clear()
            persistLocked()
            // persistLocked rotates the previous credentials into the backup
            // file, so drop both to leave no plaintext behind.
            configDirProvider?.invoke()?.let { deleteStoreFile(networkCredentialsFile(it)) }
        }
    }

    fun rememberFromInput(input: String?) {
        val normalizedInput = input?.trim().takeUnless { it.isNullOrBlank() } ?: return
        parseSmbSourceSpecFromInput(normalizedInput)?.let { spec ->
            rememberIfPresent(spec)
            return
        }
        parseHttpSourceSpecFromInput(normalizedInput)?.let(::rememberIfPresent)
    }

    private fun rememberIfPresent(spec: SmbSourceSpec) {
        if (spec.username.isNullOrBlank() && spec.password.isNullOrBlank()) return
        remember(spec)
    }

    private fun rememberIfPresent(spec: HttpSourceSpec) {
        if (spec.username.isNullOrBlank() && spec.password.isNullOrBlank()) return
        remember(spec)
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        smbCredentials.clear()
        httpCredentials.clear()
        val legacyKey = AppPreferenceKeys.NETWORK_CREDENTIALS_JSON
        val file = configDirProvider?.invoke()?.let { File(it, NETWORK_CREDENTIALS_FILE_NAME) }
        val fileRaw = file?.let { firstParsableJson(readCandidateTexts(it), isObject = true) }
        if (fileRaw != null) {
            decodeCredentialsLocked(fileRaw)
            clearLegacyDomainKey(legacyPrefsOrNull(), legacyKey)
        } else {
            val legacyRaw = legacyPrefsOrNull()?.getString(legacyKey, null)
            if (!legacyRaw.isNullOrBlank()) {
                decodeCredentialsLocked(legacyRaw)
                if (file != null) {
                    writeTextAtomic(file, encodeCredentialsLocked())
                    clearLegacyDomainKey(legacyPrefsOrNull(), legacyKey)
                }
            }
        }
        loaded = true
    }

    private fun legacyPrefsOrNull(): AppPreferences? =
        runCatching { preferencesProvider?.invoke() }.getOrNull()

    private fun decodeCredentialsLocked(raw: String) {
        runCatching {
            val root = JSONObject(raw)
            val version = root.optInt("version", NETWORK_CREDENTIALS_STORE_VERSION)
            if (version == NETWORK_CREDENTIALS_STORE_VERSION) {
                root.optJSONArray("smb")?.let(::loadSmbCredentialsLocked)
                root.optJSONArray("http")?.let(::loadHttpCredentialsLocked)
            }
        }
    }

    private fun loadSmbCredentialsLocked(array: JSONArray) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val host = item.optString("host", "").trim().lowercase(Locale.ROOT)
            if (host.isBlank()) continue
            val share = item.optString("share", "").trim().lowercase(Locale.ROOT)
            val username = item.optString("username", "").trim().ifBlank { null }
            val password = item.optString("password", "").trim().ifBlank { null }
            if (username == null && password == null) continue
            smbCredentials["$host|$share"] = StoredNetworkCredential(username, password)
        }
    }

    private fun loadHttpCredentialsLocked(array: JSONArray) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val scheme = item.optString("scheme", "").trim().lowercase(Locale.ROOT)
            val host = item.optString("host", "").trim().lowercase(Locale.ROOT)
            if ((scheme != "http" && scheme != "https") || host.isBlank()) continue
            val port = item.optInt("port", -1).takeIf { it > 0 }
            val pathScope = normalizeHttpDirectoryPath(
                item.optString("pathScope", NETWORK_CREDENTIALS_HTTP_ROOT_SCOPE)
            )
            val username = item.optString("username", "").trim().ifBlank { null }
            val password = item.optString("password", "").trim().ifBlank { null }
            if (username == null && password == null) continue
            httpCredentials
                .getOrPut("$scheme|$host|${port ?: -1}") { LinkedHashMap() }[pathScope] =
                StoredNetworkCredential(username, password)
        }
    }

    private fun encodeCredentialsLocked(): String {
        val root = JSONObject()
            .put("version", NETWORK_CREDENTIALS_STORE_VERSION)
            .put("smb", JSONArray().apply {
                smbCredentials.forEach { (key, credential) ->
                    val host = key.substringBefore('|')
                    val share = key.substringAfter('|', "")
                    put(
                        JSONObject()
                            .put("host", host)
                            .put("share", share)
                            .put("username", credential.username.orEmpty())
                            .put("password", credential.password.orEmpty())
                    )
                }
            })
            .put("http", JSONArray().apply {
                httpCredentials.forEach { (authorityKey, scopedCredentials) ->
                    val scheme = authorityKey.substringBefore('|')
                    val host = authorityKey.substringAfter('|').substringBefore('|')
                    val port = authorityKey.substringAfterLast('|').toIntOrNull()
                    scopedCredentials.forEach { (pathScope, credential) ->
                        put(
                            JSONObject()
                                .put("scheme", scheme)
                                .put("host", host)
                                .put("port", port ?: -1)
                                .put("pathScope", pathScope)
                                .put("username", credential.username.orEmpty())
                                .put("password", credential.password.orEmpty())
                        )
                    }
                }
            })
        return root.toString()
    }

    private fun persistLocked() {
        val file = configDirProvider?.invoke()?.let { File(it, NETWORK_CREDENTIALS_FILE_NAME) }
        if (file != null) {
            writeTextAtomic(file, encodeCredentialsLocked())
        } else {
            prefs().edit().putString(AppPreferenceKeys.NETWORK_CREDENTIALS_JSON, encodeCredentialsLocked()).apply()
        }
    }

    @Volatile
    var preferencesProvider: (() -> AppPreferences)? = null

    @Volatile
    var configDirProvider: (() -> File)? = null

    private fun prefs(): AppPreferences {
        return preferencesProvider?.invoke()
            ?: error("NetworkCredentialStore preferencesProvider not initialized")
    }
}

internal fun rememberEmbeddedNetworkCredentials(input: String?) {
    NetworkCredentialStore.rememberFromInput(input)
}

internal fun resolveCredentialedSmbSpec(
    input: String,
    credentialHint: String? = null
): SmbSourceSpec? {
    val parsed = parseSmbSourceSpecFromInput(input) ?: return null
    rememberEmbeddedNetworkCredentials(input)
    rememberEmbeddedNetworkCredentials(credentialHint)
    val hinted = credentialHint
        ?.let(::parseSmbSourceSpecFromInput)
        ?.takeIf { hint ->
            hint.host.equals(parsed.host, ignoreCase = true) &&
                hint.share.equals(parsed.share, ignoreCase = true)
        }
    val mergedHint = mergeCredentials(
        existingUsername = parsed.username,
        existingPassword = parsed.password,
        stored = hinted?.let {
            StoredNetworkCredential(
                username = it.username,
                password = it.password
            )
        }
    )
    return NetworkCredentialStore.applyTo(
        parsed.copy(
            username = mergedHint.first,
            password = mergedHint.second
        )
    )
}

internal fun resolveCredentialedHttpSpec(
    input: String,
    credentialHint: String? = null
): HttpSourceSpec? {
    val parsed = parseHttpSourceSpecFromInput(input) ?: return null
    rememberEmbeddedNetworkCredentials(input)
    rememberEmbeddedNetworkCredentials(credentialHint)
    val hinted = credentialHint
        ?.let(::parseHttpSourceSpecFromInput)
        ?.takeIf { hint ->
            hint.scheme.equals(parsed.scheme, ignoreCase = true) &&
                hint.host.equals(parsed.host, ignoreCase = true) &&
                (hint.port ?: -1) == (parsed.port ?: -1)
        }
    val mergedHint = mergeCredentials(
        existingUsername = parsed.username,
        existingPassword = parsed.password,
        stored = hinted?.let {
            StoredNetworkCredential(
                username = it.username,
                password = it.password
            )
        }
    )
    return NetworkCredentialStore.applyTo(
        parsed.copy(
            username = mergedHint.first,
            password = mergedHint.second
        )
    )
}

private fun mergeCredentials(
    existingUsername: String?,
    existingPassword: String?,
    stored: StoredNetworkCredential?
): Pair<String?, String?> {
    val normalizedExistingUsername = existingUsername?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedExistingPassword = existingPassword?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedStoredUsername = stored?.username?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedStoredPassword = stored?.password?.trim().takeUnless { it.isNullOrBlank() }

    if (normalizedStoredUsername == null && normalizedStoredPassword == null) {
        return normalizedExistingUsername to normalizedExistingPassword
    }
    if (normalizedExistingUsername == null && normalizedExistingPassword == null) {
        return normalizedStoredUsername to normalizedStoredPassword
    }
    if (
        normalizedExistingUsername != null &&
        normalizedStoredUsername != null &&
        !normalizedExistingUsername.equals(normalizedStoredUsername, ignoreCase = true)
    ) {
        return normalizedExistingUsername to normalizedExistingPassword
    }
    return (normalizedExistingUsername ?: normalizedStoredUsername) to
        (normalizedExistingPassword ?: normalizedStoredPassword)
}

private fun smbExactKey(spec: SmbSourceSpec): String {
    return "${spec.host.trim().lowercase(Locale.ROOT)}|${spec.share.trim().lowercase(Locale.ROOT)}"
}

private fun smbHostKey(spec: SmbSourceSpec): String {
    return "${spec.host.trim().lowercase(Locale.ROOT)}|"
}

private fun httpAuthorityKey(spec: HttpSourceSpec): String {
    return "${spec.scheme.trim().lowercase(Locale.ROOT)}|${spec.host.trim().lowercase(Locale.ROOT)}|${spec.port ?: -1}"
}

private fun httpDirectoryScope(spec: HttpSourceSpec): String {
    val normalizedPath = normalizeHttpPath(spec.path)
    return if (normalizedPath.endsWith("/")) {
        normalizeHttpDirectoryPath(normalizedPath)
    } else {
        val parent = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "")
        normalizeHttpDirectoryPath(if (parent.isBlank()) "/" else parent)
    }
}

private fun httpScopeMatches(targetScope: String, candidateScope: String): Boolean {
    if (candidateScope == NETWORK_CREDENTIALS_HTTP_ROOT_SCOPE) return true
    return targetScope == candidateScope || targetScope.startsWith(candidateScope)
}
