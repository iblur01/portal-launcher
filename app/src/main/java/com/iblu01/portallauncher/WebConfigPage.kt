package com.iblu01.portallauncher

/** Loads real web files so the UI can also be opened directly in a browser. */
internal object WebConfigPage {
    fun render(token: String): String = resource("config.html").replace("%TOKEN%", token)

    fun renderAccess(invalidCode: Boolean): String = resource("access.html")
        .replace("%INVALID_CODE%", invalidCode.toString())

    fun asset(name: String): String = resource(name)

    private fun resource(name: String): String = requireNotNull(
        WebConfigPage::class.java.getResourceAsStream("/webconfig/$name"),
    ) { "Missing web configuration resource: $name" }
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}
