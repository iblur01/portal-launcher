package com.iblu01.portallauncher

import com.iblu01.portallauncher.domain.home.HomeGroupingMode
import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.domain.home.HomeSectionIds
import com.iblu01.portallauncher.domain.home.HomeSectionPreference
import com.iblu01.portallauncher.domain.home.ManualPillGroup
import com.iblu01.portallauncher.domain.home.PillRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, Android-independent persistence format for the Home page and pinned pills.
 *
 * References are deliberately serialized as typed stable keys. Display labels and resolved Home
 * Assistant state never enter this format, so renaming an area or changing the app language does
 * not break a saved layout.
 */
object HomePillPreferencesCodec {
    const val CURRENT_SCHEMA_VERSION = 1

    const val SECTION_FAVORITES = HomeSectionIds.FAVORITES
    const val SECTION_AREAS = HomeSectionIds.AREAS
    const val SECTION_MANUAL_GROUPS = HomeSectionIds.MANUAL_GROUPS

    fun kindSectionId(kind: PillKind): String = HomeSectionIds.kind(kind)

    /** Initial migration used when the new preference has never been written. */
    fun defaults(): HomePillPreferences = HomePillPreferences(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        homePageEnabled = true,
        pinnedOrder = emptyList(),
        homeSections = listOf(
            HomeSectionPreference(
                SECTION_FAVORITES,
                visible = true,
                order = 0,
                itemOrder = emptyList(),
            ),
            HomeSectionPreference(
                SECTION_AREAS,
                visible = true,
                order = 1,
                itemOrder = emptyList(),
            ),
        ) + PillKind.values().mapIndexed { index, kind ->
            HomeSectionPreference(
                sectionId = kindSectionId(kind),
                visible = true,
                order = index + 2,
                itemOrder = emptyList(),
            )
        } + HomeSectionPreference(
            SECTION_MANUAL_GROUPS,
            visible = true,
            order = 1_000,
            itemOrder = emptyList(),
        ),
        manualGroups = emptyList(),
    )

    fun encode(preferences: HomePillPreferences): String = JSONObject().apply {
        put("schema_version", CURRENT_SCHEMA_VERSION)
        put("home_page_enabled", preferences.homePageEnabled)
        put("grouping_mode", encodeGroupingMode(preferences.groupingMode))
        put("pinned_order", JSONArray().apply {
            preferences.pinnedOrder.forEach { put(encodeRef(it)) }
        })
        put("home_sections", JSONArray().apply {
            preferences.homeSections.forEach { section ->
                put(JSONObject().apply {
                    put("section_id", section.sectionId)
                    put("visible", section.visible)
                    put("order", section.order)
                    put("item_order", JSONArray().apply {
                        section.itemOrder.forEach { put(encodeRef(it)) }
                    })
                })
            }
        })
        put("manual_groups", JSONArray().apply {
            preferences.manualGroups.forEach { group ->
                put(JSONObject().apply {
                    put("id", group.id)
                    put("name", group.name)
                    if (group.icon == null) put("icon", JSONObject.NULL) else put("icon", group.icon)
                    put("members", JSONArray().apply {
                        group.members.forEach { put(encodeRef(it)) }
                    })
                })
            }
        })
    }.toString()

    /**
     * Returns `null` for malformed or newer unsupported data. Callers can then use [defaults]
     * without overwriting the source, which keeps corrupt/future JSON recoverable.
     */
    fun decode(raw: String): HomePillPreferences? = runCatching {
        val root = JSONObject(raw)
        val version = root.optInt("schema_version", CURRENT_SCHEMA_VERSION)
        if (version !in 1..CURRENT_SCHEMA_VERSION) return null

        HomePillPreferences(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            homePageEnabled = root.optBoolean("home_page_enabled", true),
            pinnedOrder = root.optJSONArray("pinned_order").decodeRefs(),
            homeSections = root.optJSONArray("home_sections").decodeSections(),
            manualGroups = root.optJSONArray("manual_groups").decodeManualGroups(),
            groupingMode = decodeGroupingMode(root.optString("grouping_mode", "")),
        )
    }.getOrNull()

    fun decodeOrDefault(raw: String): HomePillPreferences = decode(raw) ?: defaults()

    fun encodeRef(ref: PillRef): String = when (ref) {
        is PillRef.Device -> "device:${ref.entityId}"
        is PillRef.AreaGroup -> "area:${ref.areaId}"
        is PillRef.KindGroup -> "kind:${ref.kind.name}"
        is PillRef.ManualGroup -> "manual:${ref.groupId}"
    }

    fun encodeGroupingMode(mode: HomeGroupingMode): String = when (mode) {
        HomeGroupingMode.BY_TYPE -> "type"
        HomeGroupingMode.BY_ROOM -> "room"
    }

    fun decodeGroupingMode(raw: String): HomeGroupingMode = when (raw) {
        "room" -> HomeGroupingMode.BY_ROOM
        else -> HomeGroupingMode.BY_TYPE
    }

    fun decodeRef(raw: String): PillRef? {
        val separator = raw.indexOf(':')
        if (separator <= 0 || separator == raw.lastIndex) return null
        val type = raw.substring(0, separator)
        val id = raw.substring(separator + 1)
        return when (type) {
            "device" -> PillRef.Device(id)
            "area" -> PillRef.AreaGroup(id)
            "kind" -> runCatching { PillKind.valueOf(id) }.getOrNull()?.let(PillRef::KindGroup)
            "manual" -> PillRef.ManualGroup(id)
            else -> null
        }
    }

    private fun JSONArray?.decodeRefs(): List<PillRef> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { decodeRef(optString(it)) }
    }

    private fun JSONArray?.decodeSections(): List<HomeSectionPreference> {
        if (this == null) return defaults().homeSections
        return (0 until length()).mapNotNull { index ->
            val value = optJSONObject(index) ?: return@mapNotNull null
            val id = value.optString("section_id").trim()
            if (id.isEmpty()) return@mapNotNull null
            HomeSectionPreference(
                sectionId = id,
                visible = value.optBoolean("visible", true),
                order = value.optInt("order", index),
                itemOrder = value.optJSONArray("item_order").decodeRefs(),
            )
        }
    }

    private fun JSONArray?.decodeManualGroups(): List<ManualPillGroup> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val value = optJSONObject(index) ?: return@mapNotNull null
            val id = value.optString("id").trim()
            val name = value.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
            ManualPillGroup(
                id = id,
                name = name,
                icon = if (value.has("icon") && !value.isNull("icon")) {
                    value.optString("icon").takeIf(String::isNotBlank)
                } else {
                    null
                },
                members = value.optJSONArray("members").decodeRefs().filterIsInstance<PillRef.Device>(),
            )
        }
    }
}
