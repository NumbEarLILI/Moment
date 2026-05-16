package com.example.moment.data.nas

import com.example.moment.domain.model.NasWebdavConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source

@Singleton
class WebDavHttp @Inject constructor(
    private val baseOkHttpClient: OkHttpClient
) {
    fun clientFor(config: NasWebdavConfig): OkHttpClient {
        val b = baseOkHttpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
        if (config.username.isNotEmpty() || config.password.isNotEmpty()) {
            val credential = Credentials.basic(config.username, config.password, Charsets.UTF_8)
            b.addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", credential)
                    .build()
                chain.proceed(req)
            }
        }
        if (config.trustSelfSignedCertificates) {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String
                    ) {
                    }

                    override fun checkServerTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
                        arrayOf()
                }
            )
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val tm = trustAllCerts[0] as X509TrustManager
            b.sslSocketFactory(sslContext.socketFactory, tm)
            b.hostnameVerifier { _, _ -> true }
        }
        return b.build()
    }

    suspend fun testConnection(client: OkHttpClient, baseUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = baseUrl.trim().toHttpUrlOrNull() ?: throw IOException("无效的 WebDAV 根地址")
                val propfindBody = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url(url)
                    .method("PROPFIND", propfindBody)
                    .header("Depth", "0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    when (resp.code) {
                        in 200..299, 207 -> Unit
                        401, 403 -> throw IOException("认证失败，请检查用户名与密码")
                        else -> {
                            if (!fallbackGetWorks(client, url)) {
                                throw IOException("无法访问 WebDAV（HTTP ${resp.code}）")
                            }
                        }
                    }
                }
            }
        }

    private fun fallbackGetWorks(client: OkHttpClient, url: HttpUrl): Boolean {
        val get = Request.Builder().url(url).get().build()
        client.newCall(get).execute().use { r ->
            return r.code in 200..299 || r.code == 401
        }
    }

    suspend fun mkcol(client: OkHttpClient, url: HttpUrl) {
        withContext(Dispatchers.IO) {
            val empty: RequestBody = ByteArray(0).toRequestBody(null)
            val req = Request.Builder().url(url).method("MKCOL", empty).build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    201, 204, 200 -> Unit
                    405, 301, 302, 308 -> Unit
                    409 -> throw IOException("无法在服务器上创建文件夹（路径冲突）")
                    401, 403 -> throw IOException("没有权限创建文件夹")
                    else -> if (resp.code !in 200..299) {
                        throw IOException("MKCOL 失败（HTTP ${resp.code}）")
                    }
                }
            }
        }
    }

    /**
     * 从 [root] 起依次 [segments] 执行 MKCOL，返回最终 URL。
     */
    suspend fun ensureCollectionPath(client: OkHttpClient, root: HttpUrl, segments: List<String>): HttpUrl {
        var current = root
        for (seg in segments) {
            if (seg.isEmpty()) continue
            current = current.newBuilder().addPathSegment(seg).build()
            mkcol(client, current)
        }
        return current
    }

    suspend fun putBytes(client: OkHttpClient, url: HttpUrl, bytes: ByteArray, contentType: String?) {
        withContext(Dispatchers.IO) {
            val mediaType = contentType?.toMediaType()
            val body = bytes.toRequestBody(mediaType)
            putRequest(client, url, body)
        }
    }

    suspend fun putStream(
        client: OkHttpClient,
        url: HttpUrl,
        contentLength: Long,
        contentType: String?,
        openStream: () -> java.io.InputStream
    ) {
        withContext(Dispatchers.IO) {
            val mediaType = contentType?.toMediaType()
            val body = object : RequestBody() {
                override fun contentType() = mediaType

                override fun contentLength(): Long = if (contentLength >= 0) contentLength else -1L

                override fun writeTo(sink: BufferedSink) {
                    openStream().use { input ->
                        sink.writeAll(input.source())
                    }
                }
            }
            putRequest(client, url, body)
        }
    }

    private fun putRequest(client: OkHttpClient, url: HttpUrl, body: RequestBody) {
        val req = Request.Builder().url(url).put(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("上传失败（HTTP ${resp.code}）")
            }
        }
    }

    suspend fun propfindDirectChildNames(client: OkHttpClient, collectionUrl: HttpUrl): List<String> =
        withContext(Dispatchers.IO) {
            val url = collectionUrlForPropfind(collectionUrl)
            val propfindBody = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(url)
                .method("PROPFIND", propfindBody)
                .header("Depth", "1")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299 && resp.code != 207) {
                    throw IOException("PROPFIND 失败（HTTP ${resp.code}）")
                }
                val xml = resp.body?.string().orEmpty()
                WebDavHrefParser.directChildNames(url, xml)
            }
        }

    suspend fun propfindDirectChildEntries(
        client: OkHttpClient,
        collectionUrl: HttpUrl
    ): List<WebDavHrefParser.ChildEntry> =
        withContext(Dispatchers.IO) {
            val url = collectionUrlForPropfind(collectionUrl)
            val propfindBody = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(url)
                .method("PROPFIND", propfindBody)
                .header("Depth", "1")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299 && resp.code != 207) {
                    throw IOException("PROPFIND 失败（HTTP ${resp.code}）")
                }
                val xml = resp.body?.string().orEmpty()
                WebDavHrefParser.directChildEntries(url, xml)
            }
        }

    suspend fun deleteResource(client: OkHttpClient, url: HttpUrl) {
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).delete().build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    200, 204 -> Unit
                    404 -> Unit
                    else -> if (!resp.isSuccessful) {
                        throw IOException("DELETE 失败（HTTP ${resp.code}）")
                    }
                }
            }
        }
    }

    /** 删除 WebDAV collection 及其全部内容（先删子级，再删本目录）。 */
    suspend fun deleteCollectionRecursive(client: OkHttpClient, collectionUrl: HttpUrl) {
        val base = collectionUrlForPropfind(collectionUrl)
        val children = propfindDirectChildEntries(client, base)
        for (child in children) {
            val childUrl = base.newBuilder().addPathSegment(child.name).build()
            if (child.isCollection) {
                deleteCollectionRecursive(client, childUrl)
            } else {
                deleteResource(client, childUrl)
            }
        }
        deleteResource(client, base)
    }

    suspend fun getBytes(client: OkHttpClient, url: HttpUrl): ByteArray =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("GET 失败（HTTP ${resp.code}）")
                }
                resp.body?.bytes() ?: throw IOException("空响应体")
            }
        }

    /** 流式写入本地文件，避免大图整包进内存；失败时删除不完整文件。 */
    suspend fun getToFile(client: OkHttpClient, url: HttpUrl, destination: File) {
        withContext(Dispatchers.IO) {
            val parent = destination.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("GET 失败（HTTP ${resp.code}）")
                }
                val body = resp.body ?: throw IOException("空响应体")
                FileOutputStream(destination).use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out)
                    }
                }
            }
        }
    }

    private fun collectionUrlForPropfind(url: HttpUrl): HttpUrl {
        val p = url.encodedPath
        return if (p.endsWith("/")) {
            url
        } else {
            url.newBuilder().encodedPath("$p/").build()
        }
    }

    companion object {
        private val PROPFIND_BODY =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:resourcetype/></D:prop>
            </D:propfind>
            """.trimIndent()
    }
}
