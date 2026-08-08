package com.iblu01.portallauncher.ui.icons

import com.iblu01.portallauncher.HaEntity
import org.json.JSONObject

/**
 * Resolves the icon Home Assistant would draw for an entity, so the launcher stays visually
 * continuous with the user's dashboard.
 *
 * Two layers, in HA's own order:
 *
 *  1. The entity's `icon` state attribute. This already covers the user's entity-registry override
 *     *and* the integration-supplied icon — core writes `(entry and entry.icon) or self.icon` into
 *     the attributes itself (`helpers/entity.py`), so there is nothing to read from the registry.
 *  2. The component defaults from `frontend/get_icons` (category `entity_component`), keyed by
 *     domain then device class, with a per-state override — this is what gives an entity with no
 *     explicit icon the same glyph it has in Lovelace.
 *
 * Anything still unresolved is the caller's problem: they fall back to the launcher's own icon
 * table. Numeric `range` overrides (battery levels and friends) are not handled — `default` plus
 * `state` covers every entity the panels currently draw.
 */
class HaIconResolver {

    /** Raw `resources` payload of `frontend/get_icons`; small (~50 KB) and queried lazily. */
    @Volatile
    var componentIcons: JSONObject? = null

    fun refFor(entity: HaEntity): IconRef? =
        IconRef.parse(entity.attributes.optString("icon").takeIf { it.isNotBlank() })
            ?: IconRef.parse(componentIcon(entity.domain, entity.deviceClass, entity.state))

    /** `resources[domain][deviceClass or "_"]` → `state[state]`, else `default`. */
    private fun componentIcon(domain: String, deviceClass: String, state: String): String? {
        val domainIcons = componentIcons?.optJSONObject(domain) ?: return null
        val byClass = deviceClass.takeIf { it.isNotBlank() }?.let { domainIcons.optJSONObject(it) }
        return byClass.pick(state) ?: domainIcons.optJSONObject("_").pick(state)
    }

    private fun JSONObject?.pick(state: String): String? {
        if (this == null) return null
        val stateIcon = optJSONObject("state")?.optString(state)?.takeIf { it.isNotBlank() }
        return stateIcon ?: optString("default").takeIf { it.isNotBlank() }
    }
}
