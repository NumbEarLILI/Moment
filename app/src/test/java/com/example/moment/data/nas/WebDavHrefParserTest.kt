package com.example.moment.data.nas

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavHrefParserTest {

    @Test
    fun directChildNames_parsesTypicalMultistatus() {
        val collection = "https://nas.example:5006/webdav/MomentBackup/runs/".toHttpUrlOrNull()!!
        val xml = """
            <?xml version="1.0"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/webdav/MomentBackup/runs/</href>
              </response>
              <response>
                <href>/webdav/MomentBackup/runs/run_100/</href>
              </response>
              <response>
                <href>/webdav/MomentBackup/runs/run_200/</href>
              </response>
            </multistatus>
        """.trimIndent()
        val names = WebDavHrefParser.directChildNames(collection, xml)
        assertEquals(setOf("run_100", "run_200"), names.toSet())
    }

    @Test
    fun directChildNames_acceptsRelativeHref() {
        val collection = "https://nas.example/webdav/MomentBackup/runs/".toHttpUrlOrNull()!!
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>run_99/</D:href></D:response>
            </D:multistatus>
        """.trimIndent()
        val names = WebDavHrefParser.directChildNames(collection, xml)
        assertTrue(names.contains("run_99"))
    }
}
