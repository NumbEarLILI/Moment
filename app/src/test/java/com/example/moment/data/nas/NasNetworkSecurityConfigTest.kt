package com.example.moment.data.nas

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NasNetworkSecurityConfigTest {

    @Test
    fun mainNetworkSecurityConfig_allowsUserConfiguredCleartextWebDavHosts() {
        val xml = findRepoFile("app/src/main/res/xml/network_security_config.xml").readText()

        assertTrue(
            "User-configured NAS WebDAV hosts can be arbitrary LAN HTTP hosts, so release config must permit cleartext.",
            Regex("""<base-config\s+cleartextTrafficPermitted="true"""").containsMatchIn(xml)
        )
    }

    private fun findRepoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return File(relativePath)
    }
}
