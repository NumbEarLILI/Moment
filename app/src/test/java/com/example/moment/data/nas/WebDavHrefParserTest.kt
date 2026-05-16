package com.example.moment.data.nas

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun directChildEntries_detectsCollectionFromHrefTrailingSlash() {
        val collection = "https://nas.example/webdav/MomentBackup/runs/run_1/diaries/".toHttpUrlOrNull()!!
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>123/</D:href></D:response>
              <D:response><D:href>diary.json</D:href></D:response>
            </D:multistatus>
        """.trimIndent()
        val entries = WebDavHrefParser.directChildEntries(collection, xml)
        val byName = entries.associateBy { it.name }
        assertTrue(byName["123"]!!.isCollection)
        assertFalse(byName["diary.json"]!!.isCollection)
    }

    @Test
    fun directChildEntries_detectsCollectionFromProp() {
        val collection = "https://nas.example/runs/".toHttpUrlOrNull()!!
        val xml = """
            <response xmlns="DAV:">
              <href>sub</href>
              <propstat><prop><resourcetype><collection/></resourcetype></prop></propstat>
            </response>
        """.trimIndent()
        val entries = WebDavHrefParser.directChildEntries(collection, xml)
        assertEquals(1, entries.size)
        assertEquals("sub", entries[0].name)
        assertTrue(entries[0].isCollection)
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