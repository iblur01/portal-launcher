package com.iblu01.portallauncher.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.theme.AppleColors

/**
 * Logos des sources média (`app_name`, `source`, ou domaine HA du lecteur).
 * Clés normalisées : minuscules sans séparateur, donc "Apple TV", "apple_tv" et "appletv" matchent.
 */
private val PROVIDER_LOGOS: Map<String, Int> = mapOf(
    // Streaming audio
    "spotify" to R.drawable.ic_provider_spotify,
    "youtubemusic" to R.drawable.ic_provider_youtubemusic,
    "ytmusic" to R.drawable.ic_provider_youtubemusic,
    "applemusic" to R.drawable.ic_provider_applemusic,
    "itunes" to R.drawable.ic_provider_itunes,
    "tidal" to R.drawable.ic_provider_tidal,
    "amazonmusic" to R.drawable.ic_provider_amazonmusic,
    "soundcloud" to R.drawable.ic_provider_soundcloud,
    "pandora" to R.drawable.ic_provider_pandora,
    "audible" to R.drawable.ic_provider_audible,
    "pocketcasts" to R.drawable.ic_provider_pocketcasts,
    "overcast" to R.drawable.ic_provider_overcast,
    "castro" to R.drawable.ic_provider_castro,
    // Vidéo / TV
    "youtube" to R.drawable.ic_provider_youtube,
    "youtubekids" to R.drawable.ic_provider_youtubekids,
    "netflix" to R.drawable.ic_provider_netflix,
    "primevideo" to R.drawable.ic_provider_primevideo,
    "amazonprimevideo" to R.drawable.ic_provider_primevideo,
    "prime" to R.drawable.ic_provider_primevideo,
    "paramountplus" to R.drawable.ic_provider_paramountplus,
    "paramount" to R.drawable.ic_provider_paramountplus,
    "hbomax" to R.drawable.ic_provider_max,
    "max" to R.drawable.ic_provider_max,
    "hbo" to R.drawable.ic_provider_hbo,
    "appletv" to R.drawable.ic_provider_appletv,
    "twitch" to R.drawable.ic_provider_twitch,
    "crunchyroll" to R.drawable.ic_provider_crunchyroll,
    "plex" to R.drawable.ic_provider_plex,
    "jellyfin" to R.drawable.ic_provider_jellyfin,
    "emby" to R.drawable.ic_provider_emby,
    "kodi" to R.drawable.ic_provider_kodi,
    // Radio
    "tunein" to R.drawable.ic_provider_tunein,
    "tuneinradio" to R.drawable.ic_provider_tunein,
    "iheartradio" to R.drawable.ic_provider_iheartradio,
    // Enceintes, protocoles, entrées
    "sonos" to R.drawable.ic_provider_sonos,
    "sonosradio" to R.drawable.ic_provider_sonos,
    "airplay" to R.drawable.ic_provider_airplay,
    "airplayaudio" to R.drawable.ic_provider_airplay,
    "cast" to R.drawable.ic_provider_cast,
    "chromecast" to R.drawable.ic_provider_cast,
    "googlecast" to R.drawable.ic_provider_cast,
    "dlna" to R.drawable.ic_provider_dlna,
    "dlnadmr" to R.drawable.ic_provider_dlna,
    "upnp" to R.drawable.ic_provider_dlna,
    "bluetooth" to R.drawable.ic_provider_bluetooth,
    "roon" to R.drawable.ic_provider_roon,
    "roku" to R.drawable.ic_provider_roku,
    "androidtv" to R.drawable.ic_provider_android,
    "android" to R.drawable.ic_provider_android,
    "firetv" to R.drawable.ic_provider_amazonfiretv,
    "amazonfiretv" to R.drawable.ic_provider_amazonfiretv,
    "webostv" to R.drawable.ic_provider_lg,
    "webos" to R.drawable.ic_provider_lg,
    "lg" to R.drawable.ic_provider_lg,
    "samsungtv" to R.drawable.ic_provider_samsung,
    "samsung" to R.drawable.ic_provider_samsung,
    "homeassistant" to R.drawable.ic_provider_homeassistant,
)

/** `null` quand aucun logo connu — l'appelant retombe sur son affichage texte. */
@DrawableRes
fun mediaProviderLogo(source: String?): Int? {
    val key = source?.lowercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() } ?: return null
    return PROVIDER_LOGOS[key]
}

/** Badge blanc à coins arrondis contenant le logo du fournisseur, calé sur une hauteur fixe. */
@Composable
fun MediaProviderBadge(
    @DrawableRes logo: Int,
    name: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 48.dp,
) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(0.5.dp, AppleColors.frostedBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxHeight(),
        )
    }
}
