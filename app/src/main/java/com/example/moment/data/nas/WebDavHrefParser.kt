package com.example.moment.data.nas

import java.net.URLDecoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 解析 WebDAV PROPFIND（Depth:1）响应中的 [href]，得到 [collectionUrl] 的直接子级路径最后一段（文件夹或文件名）。
 */
object WebDavHrefParser {

    data class ChildEntry(
        val name: String,
        val isCollection: Boolean
    )

    /**
     * 解析 Depth:1 的 PROPFIND 多状态响应，得到直接子级名称及是否为 collection（文件夹）。
     * 无 [resourcetype] 时，根据 href 是否以 `/` 结尾推断。
     */
    fun directChildEntries(collectionUrl: HttpUrl, xml: String): List<ChildEntry> {
        val parentPrefix = collectionPathPrefix(collectionUrl)
        val responseRegex = Regex(
            "<(?:[^>:/]+:)?response\\b[^>]*>([\\s\\S]*?)</(?:[^>:/]+:)?response>",
            RegexOption.IGNORE_CASE
        )
        val hrefRegex = Regex("<(?:[^>:/]+:)?href>\\s*([^<]+?)\\s*</(?:[^>:/]+:)?href>", RegexOption.IGNORE_CASE)
        val collectionTypeRegex = Regex("<(?:[^>:/]+:)?collection\\b", RegexOption.IGNORE_CASE)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ChildEntry>()
        for (m in responseRegex.findAll(xml)) {
            val block = m.groupValues[1]
            val hrefMatch = hrefRegex.find(block) ?: continue
            val raw = hrefMatch.groupValues[1]
            val path = hrefToEncodedPath(raw, collectionUrl) ?: continue
            val norm = path.trimEnd('/')
            val childPrefix = parentPrefix.trimEnd('/') + "/"
            if (!norm.startsWith(childPrefix)) continue
            val rel = norm.removePrefix(childPrefix).trim('/')
            if (rel.isEmpty()) continue
            if ('/' in rel) continue
            if (!seen.add(rel)) continue
            val isCollection = collectionTypeRegex.containsMatchIn(block) || raw.trim().endsWith("/")
            out.add(ChildEntry(rel, isCollection))
        }
        return out
    }

    fun directChildNames(collectionUrl: HttpUrl, xml: String): List<String> {
        val parentPrefix = collectionPathPrefix(collectionUrl)
        val regex = Regex("<(?:[^>:/]+:)?href>\\s*([^<]+?)\\s*</(?:[^>:/]+:)?href>", RegexOption.IGNORE_CASE)
        val out = mutableSetOf<String>()
        for (m in regex.findAll(xml)) {
            val raw = m.groupValues[1]
            val path = hrefToEncodedPath(raw, collectionUrl) ?: continue
            val norm = path.trimEnd('/')
            if (norm.isEmpty()) continue
            val childPrefix = parentPrefix.trimEnd('/') + "/"
            if (!norm.startsWith(childPrefix)) continue
            val rel = norm.removePrefix(childPrefix).trim('/')
            if (rel.isEmpty()) continue
            if ('/' in rel) continue
            out.add(rel)
        }
        return out.toList()
    }

    fun collectionPathPrefix(collectionUrl: HttpUrl): String {
        val clean = collectionUrl.newBuilder().query(null).fragment(null).build()
        val p = clean.encodedPath.trimEnd('/')
        return "$p/"
    }

    private fun hrefToEncodedPath(href: String, collectionUrl: HttpUrl): String? {
        val h = decodeXmlEntities(href.trim())
        val decoded = try {
            URLDecoder.decode(h, Charsets.UTF_8.name())
        } catch (_: Exception) {
            h
        }
        return when {
            decoded.startsWith("http://") || decoded.startsWith("https://") -> {
                val u = decoded.toHttpUrlOrNull() ?: return null
                u.encodedPath.trimEnd('/')
            }
            decoded.startsWith("/") -> decoded.substringBefore('?').substringBefore('#').trimEnd('/')
            else -> {
                val parent = collectionUrl.newBuilder().query(null).fragment(null).build()
                    .encodedPath.trimEnd('/')
                (parent + "/" + decoded).replace("//", "/")
                    .substringBefore('?')
                    .substringBefore('#')
                    .trimEnd('/')
            }
        }
    }

    private fun decodeXmlEntities(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
}
