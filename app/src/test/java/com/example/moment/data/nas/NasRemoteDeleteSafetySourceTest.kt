package com.example.moment.data.nas

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NasRemoteDeleteSafetySourceTest {

    @Test
    fun automaticArchiveDeleteUsesSingleDeleteNotRecursiveChildDeletion() {
        val launcher = findRepoFile(
            "app/src/main/java/com/example/moment/data/nas/NasArchiveSyncLauncher.kt"
        ).readText()
        val repository = findRepoFile(
            "app/src/main/java/com/example/moment/data/nas/NasBackupRepositoryImpl.kt"
        ).readText()

        assertTrue(launcher.contains("deleteArchiveDay(cfg, dateEpochDay)"))
        assertFalse(
            "Automatic archive delete must not recursively delete children before the final delete; partial failures can remove remote content.",
            Regex("""override suspend fun deleteArchiveDay[\s\S]*deleteCollectionRecursive""")
                .containsMatchIn(repository)
        )
        assertTrue(repository.contains("webDavHttp.deleteCollection("))
    }

    @Test
    fun propfind404IsNotSilentlyTreatedAsEmptyDirectory() {
        val webDavHttp = findRepoFile(
            "app/src/main/java/com/example/moment/data/nas/WebDavHttp.kt"
        ).readText()

        assertFalse(webDavHttp.contains("resp.code == 404"))
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
