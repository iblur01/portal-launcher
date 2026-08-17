package com.iblu01.portallauncher

/** Loads real web files so the UI can also be opened directly in a browser. */
internal object WebConfigPage {
    fun render(token: String, language: String = "fr"): String = localized(resource("config.html"), language)
        .replace("%TOKEN%", token)

    fun renderAccess(invalidCode: Boolean, language: String = "fr"): String = localized(resource("access.html"), language)
        .replace("%INVALID_CODE%", invalidCode.toString())

    fun asset(name: String, language: String = "fr"): String = localized(resource(name), language)

    private fun localized(source: String, language: String): String {
        val prepared = source.replace("%LANG%", language)
        if (language != "en") return prepared
        return english.entries.fold(prepared.replace("lang=\"fr\"", "lang=\"en\"")) { text, (french, english) ->
            text.replace(french, english)
        }
    }

    /** French remains the source layout; this table also covers every message emitted by its JS. */
    private val english = linkedMapOf(
        "Les adresses en <strong>.local</strong> utilisent le DNS local (mDNS), qui peut ne pas fonctionner sur certains écrans. Si la connexion échoue, utilisez plutôt l’adresse IP locale du serveur." to "Addresses ending in <strong>.local</strong> use local DNS (mDNS), which may not work on some panels. If the connection fails, use the server's local IP address instead.",
        "Rouvrez « Configurer depuis un autre appareil » sur le panneau, puis scannez le nouveau QR code." to "Open “Configure from another device” again on the panel, then scan the new QR code.",
        "Home Assistant refuse ce jeton. Vérifiez qu’il s’agit d’un jeton longue durée valide." to "Home Assistant rejected this token. Check that it is a valid long-lived access token.",
        "Serveur MQTT introuvable. Si son adresse se termine par .local, essayez son adresse IP locale." to "MQTT server not found. If its address ends in .local, try its local IP address.",
        "Adresse introuvable. Si elle se termine par .local, essayez l’adresse IP locale." to "Address not found. If it ends in .local, try the local IP address.",
        "La connexion MQTT a été refusée. Vérifiez l’identifiant et le mot de passe." to "The MQTT connection was rejected. Check the username and password.",
        "Les identifiants MQTT sont distincts de ceux de Home Assistant." to "MQTT credentials are separate from Home Assistant credentials.",
        "Les tests ont réussi, mais l’enregistrement a échoué." to "The tests passed, but the settings could not be saved.",
        "Indiquez l’adresse de votre maison et son jeton d’accès." to "Enter your home's address and its access token.",
        "Ajoutez le serveur utilisé pour piloter le panneau à distance." to "Add the server used to control the panel remotely.",
        "Tout est prêt. Confirmez pour appliquer les modifications." to "Everything is ready. Confirm to apply the changes.",
        "Adresse trouvée, mais le port Home Assistant est inaccessible." to "Address found, but the Home Assistant port is unreachable.",
        "Le serveur répond, mais l’API Home Assistant est inaccessible." to "The server responded, but the Home Assistant API is unreachable.",
        "Serveur trouvé, mais le port MQTT est inaccessible." to "Server found, but the MQTT port is unreachable.",
        "Saisissez une adresse commençant par http:// ou https://." to "Enter an address beginning with http:// or https://.",
        "Renseignez un serveur et un port compris entre 1 et 65535." to "Enter a server and a port between 1 and 65535.",
        "Le téléphone et le panneau doivent utiliser le même réseau Wi-Fi." to "The phone and panel must use the same Wi-Fi network.",
        "Le launcher n’est pas en mode configuration" to "The launcher is not in configuration mode",
        "Le panneau a bien reçu les nouveaux réglages." to "The panel received the new settings.",
        "Code incorrect. Consultez le panneau pour le vérifier." to "Incorrect code. Check the code shown on the panel.",
        "Saisissez le code affiché sur le panneau." to "Enter the code shown on the panel.",
        "Configuration du panneau" to "Panel configuration",
        "Configuration à distance" to "Remote configuration",
        "Configuration enregistrée" to "Configuration saved",
        "Vous pouvez fermer cet onglet." to "You can close this tab.",
        "Connectez Home Assistant" to "Connect Home Assistant",
        "Vérifiez la configuration" to "Review the configuration",
        "Configurez MQTT" to "Configure MQTT",
        "Le jeton d’accès est nécessaire." to "The access token is required.",
        "Le test de connexion a échoué." to "The connection test failed.",
        "Le test MQTT a échoué." to "The MQTT test failed.",
        "Jeton longue durée" to "Long-lived access token",
        "Jeton Home Assistant" to "Home Assistant token",
        "Nom du panneau" to "Panel name",
        "Panneau salon" to "Living room panel",
        "Mot de passe" to "Password",
        "Utilisateur" to "Username",
        "Authentification" to "Authentication",
        "Afficher le jeton" to "Show token",
        "Masquer le jeton" to "Hide token",
        "Code d’accès" to "Access code",
        "Choisir la langue" to "Choose language",
        "Le code contient 8 caractères." to "The code contains 8 characters.",
        "Maison · étape 1 sur 3" to "Home · step 1 of 3",
        " · étape " to " · step ",
        " sur " to " of ",
        "Progression" to "Progress",
        "Vérification du panneau…" to "Checking the panel…",
        "Vérification…" to "Checking…",
        "Test en cours…" to "Testing…",
        "Enregistrement…" to "Saving…",
        "Connexion interrompue" to "Connection interrupted",
        "En attente" to "Waiting",
        "Enregistrer" to "Save",
        "Continuer" to "Continue",
        "Précédent" to "Back",
        "Réessayer" to "Try again",
        "Terminé" to "Done",
        "Validé" to "Passed",
        "Échec" to "Failed",
        "Vérification" to "Review",
        "Contrôle" to "Control",
        "Maison" to "Home",
        "Serveur" to "Server",
        "Adresse" to "Address",
        "Jeton" to "Token",
        "Panneau" to "Panel",
        "Port" to "Port",
    )

    private fun resource(name: String): String = requireNotNull(
        WebConfigPage::class.java.getResourceAsStream("/webconfig/$name"),
    ) { "Missing web configuration resource: $name" }
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}
