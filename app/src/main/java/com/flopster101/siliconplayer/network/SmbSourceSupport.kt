package com.flopster101.siliconplayer

import android.net.Uri
import java.util.Locale

internal data class SmbSourceSpec(
    val host: String,
    val share: String,
    val path: String? = null,
    val username: String? = null,
    val password: String? = null
)

internal fun buildSmbSourceSpec(
    host: String,
    share: String,
    path: String?,
    username: String?,
    password: String?
): SmbSourceSpec? {
    val normalizedHost = host.trim().lowercase(Locale.ROOT).ifBlank { return null }
    val normalizedShare = share.trim().trim('/').trim('\\')
    val normalizedPath = if (normalizedShare.isBlank()) {
        null
    } else {
        normalizeSmbPathForShare(path)
    }
    val normalizedUsername = username?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedPassword = password?.trim().takeUnless { it.isNullOrBlank() }
    return SmbSourceSpec(
        host = normalizedHost,
        share = normalizedShare,
        path = normalizedPath,
        username = normalizedUsername,
        password = normalizedPassword
    )
}

internal fun parseSmbSourceSpecFromInput(input: String): SmbSourceSpec? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("smb://", ignoreCase = true)) return null
    val withoutScheme = trimmed.substringAfter("://", missingDelimiterValue = "")
    if (withoutScheme.isBlank()) return null

    val authorityAndPath = withoutScheme
        .substringBefore('#')
        .substringBefore('?')
    val authorityPart = authorityAndPath.substringBefore('/').trim()
    val rawPathPart = authorityAndPath.substringAfter('/', missingDelimiterValue = "").trim()
    if (authorityPart.isBlank()) return null

    val userInfoPart = authorityPart.substringBefore('@', missingDelimiterValue = "")
        .takeIf { authorityPart.contains('@') }
    val hostPartRaw = authorityPart.substringAfter('@', authorityPart).trim()
    val host = hostPartRaw
        .removePrefix("[")
        .removeSuffix("]")
        .trim()
    if (host.isBlank()) return null

    val segments = rawPathPart
        .replace('\\', '/')
        .split('/')
        .map { segment -> safeUriDecode(segment).trim() }
        .filter { it.isNotBlank() }
    val share = segments.firstOrNull().orEmpty()
    val relativePath = if (share.isBlank()) null else segments.drop(1).joinToString("/").ifBlank { null }
    val (username, password) = parseSmbUserInfo(userInfoPart)
    return buildSmbSourceSpec(
        host = host,
        share = share,
        path = relativePath,
        username = username,
        password = password
    )
}

internal fun buildSmbSourceId(spec: SmbSourceSpec): String {
    return buildSmbUri(
        spec = spec.copy(
            username = null,
            password = null
        ),
        includePassword = false
    )
}

internal fun buildSmbRequestUri(spec: SmbSourceSpec): String {
    return buildSmbUri(spec = spec, includePassword = true)
}

internal fun resolveSmbDisplayHost(
    host: String,
    networkNodes: List<NetworkNode> = emptyList()
): String {
    val trimmedHost = host.trim().removePrefix("[").removeSuffix("]").trim()
    if (trimmedHost.isBlank()) return host
    val node = networkNodes.firstOrNull { node ->
        node.type == NetworkNodeType.RemoteSource &&
            node.sourceKind == NetworkSourceKind.Smb &&
            (node.smbHost?.trim().equals(trimmedHost, ignoreCase = true) ||
                node.source?.let(::parseSmbSourceSpecFromInput)?.host?.trim().equals(trimmedHost, ignoreCase = true))
    }
    val candidate = node?.smbDiscoveredHostName?.trim().takeUnless { it.isNullOrBlank() }
        ?: node?.title?.trim().takeUnless { it.isNullOrBlank() || it.startsWith("smb://", ignoreCase = true) }
    if (!candidate.isNullOrBlank()) {
        return candidate
    }
    return trimmedHost
}

internal fun buildSmbDisplayUri(
    spec: SmbSourceSpec,
    networkNodes: List<NetworkNode> = emptyList()
): String {
    val displayHost = resolveSmbDisplayHost(spec.host, networkNodes)
    val path = normalizeSmbPathForShare(spec.path)
    return buildString {
        append("smb://")
        append(displayHost.trim())
        val share = spec.share.trim()
        if (share.isNotBlank()) {
            append("/")
            append(decodePercentEncodedForDisplay(share) ?: share)
        }
        if (share.isNotBlank() && !path.isNullOrBlank()) {
            append("/")
            val decodedPath = path.split('/').map { decodePercentEncodedForDisplay(it) ?: it }.joinToString("/")
            append(decodedPath)
        }
    }
}

internal fun formatSourceIdForDisplay(
    sourceId: String?,
    networkNodes: List<NetworkNode> = emptyList()
): String {
    val normalized = sourceId?.trim().takeUnless { it.isNullOrBlank() } ?: return "Unavailable"
    parseSmbSourceSpecFromInput(normalized)?.let { smbSpec ->
        return buildSmbDisplayUri(smbSpec, networkNodes)
    }
    parseHttpSourceSpecFromInput(normalized)?.let { httpSpec ->
        return buildHttpDisplayUri(httpSpec)
    }
    return normalized
}

private fun buildSmbUri(spec: SmbSourceSpec, includePassword: Boolean): String {
    val authorityHost = buildSmbAuthorityHost(spec.host)
    val normalizedUsername = spec.username?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedPassword = spec.password?.trim().takeUnless { it.isNullOrBlank() }
    val encodedUserInfo = if (normalizedUsername == null) {
        null
    } else {
        buildString {
            append(safeUriEncode(normalizedUsername))
            if (includePassword && normalizedPassword != null) {
                append(":")
                append(safeUriEncode(normalizedPassword))
            }
        }
    }
    val authority = if (encodedUserInfo == null) {
        authorityHost
    } else {
        "$encodedUserInfo@$authorityHost"
    }
    val builder = Uri.Builder()
        .scheme("smb")
        .encodedAuthority(authority)
    val share = spec.share.trim()
    if (share.isNotBlank()) {
        builder.appendPath(share)
        normalizeSmbPathForShare(spec.path)
            ?.split('/')
            ?.filter { it.isNotBlank() }
            ?.forEach { segment ->
                builder.appendPath(segment)
            }
    }
    return builder.build().toString()
}

private fun safeUriDecode(value: String): String {
    return runCatching { Uri.decode(value) }.getOrElse {
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}

private fun safeUriEncode(value: String): String {
    return runCatching { Uri.encode(value) }.getOrElse {
        runCatching { java.net.URLEncoder.encode(value, "UTF-8") }.getOrDefault(value)
    }
}

private fun buildSmbAuthorityHost(rawHost: String): String {
    val host = rawHost.trim()
    if (host.contains(':') && !host.startsWith("[") && !host.endsWith("]")) {
        return "[$host]"
    }
    return host
}

private fun parseSmbUserInfo(encodedUserInfo: String?): Pair<String?, String?> {
    if (encodedUserInfo.isNullOrBlank()) return Pair(null, null)
    val userPart = encodedUserInfo.substringBefore(':')
    val passwordPart = encodedUserInfo
        .substringAfter(':', missingDelimiterValue = "")
        .takeIf { it.isNotEmpty() }
    val username = safeUriDecode(userPart).trim().ifBlank { null }
    val password = passwordPart?.let { safeUriDecode(it).trim().ifBlank { null } }
    return Pair(username, password)
}
