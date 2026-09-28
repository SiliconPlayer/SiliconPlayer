package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.data.resolveArchiveContainerParentLocation
import java.io.File
import java.net.URI
import java.util.Locale

internal data class BrowserOpenTarget(
    val locationId: String?,
    val directoryPath: String,
    val smbSourceNodeId: Long?,
    val httpSourceNodeId: Long?
)

internal data class SmbTargetResolution(
    val sourceNodeId: Long?,
    val requestUri: String
)

internal data class HttpTargetResolution(
    val sourceNodeId: Long?,
    val requestUri: String
)

internal fun resolveBrowserFolderForRecentSource(
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>
): BrowserOpenTarget? {
    parseArchiveSourceId(entry.path)?.let { archiveSource ->
        val parentInArchive = archiveSource.entryPath
            .replace('\\', '/')
            .substringBeforeLast('/', "")
            .trim('/')
        val logicalDirectory = buildArchiveDirectoryPath(
            archivePath = archiveSource.archivePath,
            inArchiveDirectoryPath = parentInArchive.ifBlank { null }
        )
        val archiveSmb = parseSmbSourceSpecFromInput(archiveSource.archivePath)
        val archiveHttp = parseHttpSourceSpecFromInput(archiveSource.archivePath)
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = logicalDirectory,
            smbSourceNodeId = if (archiveSmb != null) entry.sourceNodeId else null,
            httpSourceNodeId = if (archiveHttp != null) entry.sourceNodeId else null
        )
    }

    val trimmedPath = entry.path.trim()
    val scheme = trimmedPath.substringBefore(':').lowercase(Locale.ROOT).trim()
    if (scheme == "smb") {
        val smbSpec = parseSmbSourceSpecFromInput(entry.path) ?: return null
        if (smbSpec.share.isBlank()) return null
        val normalizedPath = smbSpec.path?.trim().orEmpty()
        val parentSpec = if (normalizedPath.isBlank()) {
            smbSpec.copy(path = null)
        } else {
            val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "")
                .trim()
                .ifBlank { null }
            smbSpec.copy(path = parentPath)
        }
        val smbTarget = resolveSmbRecentOpenTarget(
            targetSpec = parentSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = smbTarget.requestUri,
            smbSourceNodeId = smbTarget.sourceNodeId,
            httpSourceNodeId = null
        )
    }
    if (scheme == "http" || scheme == "https") {
        val httpSpec = parseHttpSourceSpecFromInput(entry.path) ?: return null
        val normalizedPath = normalizeHttpPath(httpSpec.path)
        val parentPath = normalizedPath
            .trimEnd('/')
            .substringBeforeLast('/', missingDelimiterValue = "")
            .trim()
        val parentSpec = httpSpec.copy(
            path = if (parentPath.isBlank()) "/" else "$parentPath/",
            query = null
        )
        val httpTarget = resolveHttpRecentOpenTarget(
            targetSpec = parentSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = httpTarget.requestUri,
            smbSourceNodeId = null,
            httpSourceNodeId = httpTarget.sourceNodeId
        )
    }

    val localPath = if (scheme == "file") {
        runCatching { URI(trimmedPath).path }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: trimmedPath.substringAfter(':').trim().takeIf { it.isNotBlank() }
    } else {
        trimmedPath.takeIf { it.isNotBlank() }
    } ?: return null

    val localFile = File(localPath)
    val directory = if (localFile.isDirectory) {
        localFile.absolutePath
    } else {
        localFile.parentFile?.absolutePath
    } ?: return null
    return BrowserOpenTarget(
        locationId = entry.locationId,
        directoryPath = directory,
        smbSourceNodeId = null,
        httpSourceNodeId = null
    )
}

internal fun resolveBrowserParentForRecentFolder(
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>
): BrowserOpenTarget? {
    parseArchiveLogicalPath(entry.path)?.let { (archivePath, entryPath) ->
        if (entryPath.isNullOrBlank()) {
            val archiveParent = resolveArchiveContainerParentLocation(archivePath) ?: return null
            val archiveSmb = parseSmbSourceSpecFromInput(archivePath)
            val archiveHttp = parseHttpSourceSpecFromInput(archivePath)
            val parentSmbSpec = parseSmbSourceSpecFromInput(archiveParent)
            val parentHttpSpec = parseHttpSourceSpecFromInput(archiveParent)
            val resolvedArchiveParent = when {
                parentSmbSpec != null -> resolveSmbRecentOpenTarget(
                    targetSpec = parentSmbSpec,
                    networkNodes = networkNodes,
                    preferredSourceNodeId = entry.sourceNodeId
                ).requestUri
                parentHttpSpec != null -> resolveHttpRecentOpenTarget(
                    targetSpec = parentHttpSpec,
                    networkNodes = networkNodes,
                    preferredSourceNodeId = entry.sourceNodeId
                ).requestUri
                else -> archiveParent
            }
            return BrowserOpenTarget(
                locationId = null,
                directoryPath = resolvedArchiveParent,
                smbSourceNodeId = if (archiveSmb != null) entry.sourceNodeId else null,
                httpSourceNodeId = if (archiveHttp != null) entry.sourceNodeId else null
            )
        }
        val parentInArchive = entryPath
            .replace('\\', '/')
            .trim('/')
            .substringBeforeLast('/', "")
            .trim('/')
        val logicalParent = buildArchiveDirectoryPath(
            archivePath = archivePath,
            inArchiveDirectoryPath = parentInArchive.ifBlank { null }
        )
        val archiveSmb = parseSmbSourceSpecFromInput(archivePath)
        val archiveHttp = parseHttpSourceSpecFromInput(archivePath)
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = logicalParent,
            smbSourceNodeId = if (archiveSmb != null) entry.sourceNodeId else null,
            httpSourceNodeId = if (archiveHttp != null) entry.sourceNodeId else null
        )
    }

    val rawPath = entry.path.trim().takeIf { it.isNotBlank() } ?: return null
    val smbSpec = parseSmbSourceSpecFromInput(rawPath)
    if (smbSpec != null) {
        if (smbSpec.share.isBlank()) return null
        val normalizedPath = smbSpec.path?.trim().orEmpty()
        val parentSpec = if (normalizedPath.isBlank()) {
            smbSpec.copy(share = "", path = null)
        } else {
            val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "")
                .trim()
                .ifBlank { null }
            smbSpec.copy(path = parentPath)
        }
        val smbTarget = resolveSmbRecentOpenTarget(
            targetSpec = parentSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = smbTarget.requestUri,
            smbSourceNodeId = smbTarget.sourceNodeId,
            httpSourceNodeId = null
        )
    }
    val httpSpec = parseHttpSourceSpecFromInput(rawPath)
    if (httpSpec != null) {
        val normalizedPath = normalizeHttpPath(httpSpec.path)
        if (normalizedPath == "/") return null
        val parentPath = normalizedPath
            .trimEnd('/')
            .substringBeforeLast('/', missingDelimiterValue = "")
            .trim()
        val parentSpec = httpSpec.copy(
            path = if (parentPath.isBlank()) "/" else "$parentPath/",
            query = null
        )
        val httpTarget = resolveHttpRecentOpenTarget(
            targetSpec = parentSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return BrowserOpenTarget(
            locationId = null,
            directoryPath = httpTarget.requestUri,
            smbSourceNodeId = null,
            httpSourceNodeId = httpTarget.sourceNodeId
        )
    }

    val folder = File(rawPath)
    val parentPath = folder.parentFile?.absolutePath ?: return null
    return BrowserOpenTarget(
        locationId = entry.locationId,
        directoryPath = parentPath,
        smbSourceNodeId = null,
        httpSourceNodeId = null
    )
}

internal fun resolveSmbRecentOpenTarget(
    targetSpec: SmbSourceSpec,
    networkNodes: List<NetworkNode>,
    preferredSourceNodeId: Long? = null
): SmbTargetResolution {
    if (!targetSpec.password.isNullOrBlank()) {
        return SmbTargetResolution(
            sourceNodeId = null,
            requestUri = buildSmbRequestUri(targetSpec)
        )
    }

    val preferredNodeSpec = preferredSourceNodeId
        ?.let { sourceNodeId ->
            networkNodes.firstOrNull { node ->
                node.id == sourceNodeId &&
                    node.type == NetworkNodeType.RemoteSource &&
                    node.sourceKind == NetworkSourceKind.Smb
            }
        }
        ?.let(::resolveNetworkNodeSmbSpec)

    if (preferredNodeSpec != null) {
        val exactSpec = targetSpec.copy(
            username = preferredNodeSpec.username,
            password = preferredNodeSpec.password
        )
        return SmbTargetResolution(
            sourceNodeId = preferredSourceNodeId,
            requestUri = buildSmbRequestUri(exactSpec)
        )
    }

    val normalizedTargetPath = normalizeSmbPathForShare(targetSpec.path).orEmpty()
    val normalizedTargetUser = targetSpec.username?.trim().orEmpty()
    val candidate = networkNodes
        .asSequence()
        .filter { node ->
            node.type == NetworkNodeType.RemoteSource && node.sourceKind == NetworkSourceKind.Smb
        }
        .mapNotNull { node ->
            val spec = resolveNetworkNodeSmbSpec(node) ?: return@mapNotNull null
            if (!spec.host.equals(targetSpec.host, ignoreCase = true)) return@mapNotNull null
            if (!spec.share.equals(targetSpec.share, ignoreCase = true)) return@mapNotNull null
            val normalizedNodePath = normalizeSmbPathForShare(spec.path).orEmpty()
            val normalizedNodeUser = spec.username?.trim().orEmpty()
            var score = 0
            if (normalizedNodeUser.isNotEmpty() && normalizedNodeUser.equals(normalizedTargetUser, ignoreCase = true)) {
                score += 200
            }
            if (normalizedNodePath.equals(normalizedTargetPath, ignoreCase = true)) {
                score += 400
            } else {
                if (normalizedTargetPath.startsWith("$normalizedNodePath/") && normalizedNodePath.isNotBlank()) {
                    score += 100 + normalizedNodePath.length
                }
                if (normalizedNodePath.startsWith("$normalizedTargetPath/") && normalizedTargetPath.isNotBlank()) {
                    score += 40 + normalizedTargetPath.length
                }
                if (normalizedNodePath.isBlank() && normalizedTargetPath.isNotBlank()) {
                    score += 30
                }
            }
            if (!spec.password.isNullOrBlank()) score += 10
            node.id to (score to spec)
        }
        .maxByOrNull { (_, pair) -> pair.first }

    val resolvedNodeId = candidate?.first
    val credentialSpec = candidate?.second?.second
    val finalSpec = if (credentialSpec == null) {
        targetSpec
    } else {
        targetSpec.copy(
            username = credentialSpec.username,
            password = credentialSpec.password
        )
    }
    return SmbTargetResolution(
        sourceNodeId = resolvedNodeId,
        requestUri = buildSmbRequestUri(NetworkCredentialStore.applyTo(finalSpec))
    )
}

internal fun resolveHttpRecentOpenTarget(
    targetSpec: HttpSourceSpec,
    networkNodes: List<NetworkNode>,
    preferredSourceNodeId: Long? = null
): HttpTargetResolution {
    if (!targetSpec.password.isNullOrBlank()) {
        return HttpTargetResolution(
            sourceNodeId = null,
            requestUri = buildHttpRequestUri(targetSpec)
        )
    }

    val preferredNodeSpec = preferredSourceNodeId
        ?.let { sourceNodeId ->
            networkNodes.firstOrNull { node ->
                node.id == sourceNodeId &&
                    node.type == NetworkNodeType.RemoteSource &&
                    node.sourceKind != NetworkSourceKind.Smb
            }
        }
        ?.let(::resolveNetworkNodeSourceId)
        ?.let(::parseHttpSourceSpecFromInput)

    if (preferredNodeSpec != null) {
        val exactSpec = targetSpec.copy(
            username = preferredNodeSpec.username ?: targetSpec.username,
            password = preferredNodeSpec.password ?: targetSpec.password
        )
        return HttpTargetResolution(
            sourceNodeId = preferredSourceNodeId,
            requestUri = buildHttpRequestUri(exactSpec)
        )
    }

    val normalizedTargetPath = normalizeHttpDirectoryPath(targetSpec.path)
    val normalizedTargetUser = targetSpec.username?.trim().orEmpty()
    val normalizedTargetPort = targetSpec.port ?: -1
    val candidate = networkNodes
        .asSequence()
        .filter { node ->
            node.type == NetworkNodeType.RemoteSource && node.sourceKind != NetworkSourceKind.Smb
        }
        .mapNotNull { node ->
            val spec = resolveNetworkNodeSourceId(node)
                ?.let(::parseHttpSourceSpecFromInput)
                ?: return@mapNotNull null
            if (!spec.scheme.equals(targetSpec.scheme, ignoreCase = true)) return@mapNotNull null
            if (!spec.host.equals(targetSpec.host, ignoreCase = true)) return@mapNotNull null
            val normalizedNodePort = spec.port ?: -1
            if (normalizedNodePort != normalizedTargetPort) return@mapNotNull null

            val normalizedNodePath = normalizeHttpDirectoryPath(spec.path)
            val normalizedNodeUser = spec.username?.trim().orEmpty()
            var score = 0
            if (
                normalizedNodeUser.isNotEmpty() &&
                normalizedNodeUser.equals(normalizedTargetUser, ignoreCase = true)
            ) {
                score += 200
            }
            if (normalizedNodePath.equals(normalizedTargetPath, ignoreCase = true)) {
                score += 400
            } else {
                if (normalizedTargetPath.startsWith(normalizedNodePath)) {
                    score += 100 + normalizedNodePath.length
                }
                if (normalizedNodePath.startsWith(normalizedTargetPath)) {
                    score += 40 + normalizedTargetPath.length
                }
            }
            if (!spec.password.isNullOrBlank()) score += 10
            node.id to (score to spec)
        }
        .maxByOrNull { (_, pair) -> pair.first }

    val resolvedNodeId = candidate?.first
    val credentialSpec = candidate?.second?.second
    val finalSpec = if (credentialSpec == null) {
        targetSpec
    } else {
        targetSpec.copy(
            username = credentialSpec.username ?: targetSpec.username,
            password = credentialSpec.password ?: targetSpec.password
        )
    }
    return HttpTargetResolution(
        sourceNodeId = resolvedNodeId,
        requestUri = buildHttpRequestUri(NetworkCredentialStore.applyTo(finalSpec))
    )
}

internal fun resolveFolderOpenRequest(
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>
): BrowserOpenRequest {
    val archiveLogicalPath = parseArchiveLogicalPath(entry.path)
    if (archiveLogicalPath != null) {
        val archiveSourcePath = archiveLogicalPath.first
        val isArchiveSmb = parseSmbSourceSpecFromInput(archiveSourcePath) != null
        val isArchiveHttp = parseHttpSourceSpecFromInput(archiveSourcePath) != null
        return browserOpenRequest(
            locationId = null,
            directoryPath = entry.path,
            smbSourceNodeId = if (isArchiveSmb) entry.sourceNodeId else null,
            httpSourceNodeId = if (isArchiveHttp) entry.sourceNodeId else null
        )
    }
    val smbSpec = parseSmbSourceSpecFromInput(entry.path)
    if (smbSpec != null) {
        val smbTarget = resolveSmbRecentOpenTarget(
            targetSpec = smbSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return browserOpenRequest(
            directoryPath = smbTarget.requestUri,
            smbSourceNodeId = smbTarget.sourceNodeId
        )
    }
    val httpSpec = parseHttpSourceSpecFromInput(entry.path)
    if (httpSpec != null) {
        val httpTarget = resolveHttpRecentOpenTarget(
            targetSpec = httpSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        )
        return browserOpenRequest(
            directoryPath = httpTarget.requestUri,
            httpSourceNodeId = httpTarget.sourceNodeId
        )
    }
    return browserOpenRequest(
        locationId = entry.locationId,
        directoryPath = entry.path
    )
}
