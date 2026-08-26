package com.example.moment.domain.about

data class ChangelogRelease(
    val version: String,
    val body: String
)

object ChangelogParser {
    private val heading = Regex("^##\\s+(.+)$")

    fun parse(markdown: String): List<ChangelogRelease> {
        val releases = mutableListOf<ChangelogRelease>()
        var currentVersion: String? = null
        val body = StringBuilder()

        fun flush() {
            val version = currentVersion ?: return
            releases.add(ChangelogRelease(version, body.toString().trim()))
            body.setLength(0)
        }

        markdown.replace("\r\n", "\n").lines().forEach { line ->
            val match = heading.find(line)
            if (match != null) {
                flush()
                currentVersion = match.groupValues[1].trim()
            } else if (currentVersion != null) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(line)
            }
        }
        flush()
        return releases
    }
}
