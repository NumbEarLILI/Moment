package com.example.moment.data.nas

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class NasCompressedImageDecodeSourceTest {

    @Test
    fun boundsDecodeDoesNotTreatNullBitmapAsFailure() {
        val source = findRepoFile(
            "app/src/main/java/com/example/moment/data/nas/NasDiaryWebDavPackager.kt"
        ).readText()

        assertFalse(
            "BitmapFactory.decodeStream returns null for inJustDecodeBounds=true; only missing stream or invalid bounds should fail.",
            Regex("""BitmapFactory\.decodeStream\(it,\s*null,\s*bounds\)\s*\}\s*\?:\s*return null""")
                .containsMatchIn(source)
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
