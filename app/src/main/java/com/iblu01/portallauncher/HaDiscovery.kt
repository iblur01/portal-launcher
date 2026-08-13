package com.iblu01.portallauncher

object HaDiscovery {
    fun screenDiscoveryTopic(deviceId: String) = "homeassistant/switch/${deviceId}_screen/config"
    fun screenStateTopic(deviceId: String) = "portal/$deviceId/screen/state"
    fun screenCommandTopic(deviceId: String) = "portal/$deviceId/screen/command"
    fun screenConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Screen","unique_id":"${deviceId}_screen","device":${device(deviceId, name)},"state_topic":"${screenStateTopic(deviceId)}","command_topic":"${screenCommandTopic(deviceId)}","payload_on":"ON","payload_off":"OFF","state_on":"ON","state_off":"OFF"}"""
    }

    fun screenModeDiscoveryTopic(deviceId: String) = "homeassistant/sensor/${deviceId}_screen_mode/config"
    fun screenModeStateTopic(deviceId: String) = "portal/$deviceId/screen/mode"
    fun screenModeConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Screen State","unique_id":"${deviceId}_screen_mode","device":${device(deviceId, name)},"state_topic":"${screenModeStateTopic(deviceId)}","icon":"mdi:monitor"}"""
    }

    fun presenceDiscoveryTopic(deviceId: String) = "homeassistant/binary_sensor/${deviceId}_presence/config"
    fun presenceStateTopic(deviceId: String) = "portal/$deviceId/presence/state"
    fun presenceAttributesTopic(deviceId: String) = "portal/$deviceId/presence/attributes"
    fun presenceConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Presence","unique_id":"${deviceId}_presence","device":${device(deviceId, name)},"state_topic":"${presenceStateTopic(deviceId)}","json_attributes_topic":"${presenceAttributesTopic(deviceId)}","device_class":"occupancy","payload_on":"ON","payload_off":"OFF"}"""
    }

    fun lightDiscoveryTopic(deviceId: String) = "homeassistant/sensor/${deviceId}_light/config"
    fun lightStateTopic(deviceId: String) = "portal/$deviceId/sensor/light"
    fun lightConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Ambient Light","unique_id":"${deviceId}_light","device":${device(deviceId, name)},"state_topic":"${lightStateTopic(deviceId)}","device_class":"illuminance","unit_of_measurement":"lx","state_class":"measurement"}"""
    }

    fun tempDiscoveryTopic(deviceId: String) = "homeassistant/sensor/${deviceId}_temperature/config"
    fun tempStateTopic(deviceId: String) = "portal/$deviceId/sensor/temperature"
    fun tempConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Temperature","unique_id":"${deviceId}_temperature","device":${device(deviceId, name)},"state_topic":"${tempStateTopic(deviceId)}","device_class":"temperature","unit_of_measurement":"°C","state_class":"measurement"}"""
    }

    fun volumeDiscoveryTopic(deviceId: String) = "homeassistant/number/${deviceId}_volume/config"
    fun volumeStateTopic(deviceId: String) = "portal/$deviceId/audio/volume/state"
    fun volumeCommandTopic(deviceId: String) = "portal/$deviceId/audio/volume/set"
    fun volumeConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Volume","unique_id":"${deviceId}_volume","device":${device(deviceId, name)},"state_topic":"${volumeStateTopic(deviceId)}","command_topic":"${volumeCommandTopic(deviceId)}","min":0,"max":100,"step":1,"mode":"slider","icon":"mdi:volume-high"}"""
    }

    fun soundCommandTopic(deviceId: String) = "portal/$deviceId/sound/play"
    fun notificationCommandTopic(deviceId: String) = "portal/$deviceId/notification"
    fun doorbellDiscoveryTopic(deviceId: String) = "homeassistant/button/${deviceId}_doorbell/config"
    fun alertDiscoveryTopic(deviceId: String) = "homeassistant/button/${deviceId}_alert/config"
    fun doorbellConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Doorbell","unique_id":"${deviceId}_doorbell","device":${device(deviceId, name)},"command_topic":"${soundCommandTopic(deviceId)}","payload_press":"doorbell","icon":"mdi:bell-ring"}"""
    }
    fun alertConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Alert","unique_id":"${deviceId}_alert","device":${device(deviceId, name)},"command_topic":"${soundCommandTopic(deviceId)}","payload_press":"alert","icon":"mdi:alert"}"""
    }

    fun volumeMuteDiscoveryTopic(deviceId: String) = "homeassistant/switch/${deviceId}_volume_mute/config"
    fun volumeMuteStateTopic(deviceId: String) = "portal/$deviceId/audio/mute/state"
    fun volumeMuteCommandTopic(deviceId: String) = "portal/$deviceId/audio/mute/set"
    fun volumeMuteConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Volume Mute","unique_id":"${deviceId}_volume_mute","device":${device(deviceId, name)},"state_topic":"${volumeMuteStateTopic(deviceId)}","command_topic":"${volumeMuteCommandTopic(deviceId)}","payload_on":"ON","payload_off":"OFF","state_on":"ON","state_off":"OFF","icon":"mdi:volume-off"}"""
    }

    fun brightnessDiscoveryTopic(deviceId: String) = "homeassistant/number/${deviceId}_brightness/config"
    fun brightnessStateTopic(deviceId: String) = "portal/$deviceId/display/brightness/state"
    fun brightnessCommandTopic(deviceId: String) = "portal/$deviceId/display/brightness/set"
    fun brightnessConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Brightness","unique_id":"${deviceId}_brightness","device":${device(deviceId, name)},"state_topic":"${brightnessStateTopic(deviceId)}","command_topic":"${brightnessCommandTopic(deviceId)}","min":0,"max":100,"step":1,"mode":"slider","icon":"mdi:brightness-6"}"""
    }

    fun screenTimeoutDiscoveryTopic(deviceId: String) =
        "homeassistant/switch/${deviceId}_screen_timeout/config"
    fun screenTimeoutStateTopic(deviceId: String) = "portal/$deviceId/screen_timeout/state"
    fun screenTimeoutCommandTopic(deviceId: String) = "portal/$deviceId/screen_timeout/set"
    fun screenTimeoutConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Screen Timeout","unique_id":"${deviceId}_screen_timeout","device":${device(deviceId, name)},"state_topic":"${screenTimeoutStateTopic(deviceId)}","command_topic":"${screenTimeoutCommandTopic(deviceId)}","payload_on":"ON","payload_off":"OFF","state_on":"ON","state_off":"OFF","icon":"mdi:timer-off","entity_category":"config"}"""
    }

    fun screenTimeoutMinutesDiscoveryTopic(deviceId: String) =
        "homeassistant/number/${deviceId}_screen_timeout_mins/config"
    fun screenTimeoutMinutesStateTopic(deviceId: String) = "portal/$deviceId/screen_timeout_mins/state"
    fun screenTimeoutMinutesCommandTopic(deviceId: String) = "portal/$deviceId/screen_timeout_mins/set"
    fun screenTimeoutMinutesConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Screen Timeout Minutes","unique_id":"${deviceId}_screen_timeout_mins","device":${device(deviceId, name)},"state_topic":"${screenTimeoutMinutesStateTopic(deviceId)}","command_topic":"${screenTimeoutMinutesCommandTopic(deviceId)}","min":1,"max":240,"step":1,"mode":"box","unit_of_measurement":"min","icon":"mdi:timer-cog","entity_category":"config"}"""
    }

    fun powerModeDiscoveryTopic(deviceId: String) =
        "homeassistant/select/${deviceId}_power_mode/config"
    fun powerModeStateTopic(deviceId: String) = "portal/$deviceId/power_mode/state"
    fun powerModeCommandTopic(deviceId: String) = "portal/$deviceId/power_mode/set"
    fun powerModeConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Power Mode","unique_id":"${deviceId}_power_mode","device":${device(deviceId, name)},"state_topic":"${powerModeStateTopic(deviceId)}","command_topic":"${powerModeCommandTopic(deviceId)}","options":["Follow presence","Always on"],"icon":"mdi:power-settings","entity_category":"config"}"""
    }

    fun photoStatusDiscoveryTopic(deviceId: String) = "homeassistant/sensor/${deviceId}_photo_status/config"
    fun photoStatusStateTopic(deviceId: String) = "portal/$deviceId/photo/status"
    fun photoStatusAttributesTopic(deviceId: String) = "portal/$deviceId/photo/attributes"
    fun photoStatusConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Photo Source","unique_id":"${deviceId}_photo_status","device":${device(deviceId, name)},"state_topic":"${photoStatusStateTopic(deviceId)}","json_attributes_topic":"${photoStatusAttributesTopic(deviceId)}","expire_after":15,"icon":"mdi:image","entity_category":"diagnostic"}"""
    }

    fun ipDiscoveryTopic(deviceId: String) = "homeassistant/sensor/${deviceId}_ip/config"
    fun ipStateTopic(deviceId: String) = "portal/$deviceId/sensor/ip"
    fun ipConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"IP Address","unique_id":"${deviceId}_ip","device":${device(deviceId, name)},"state_topic":"${ipStateTopic(deviceId)}","icon":"mdi:ip-network","entity_category":"diagnostic"}"""
    }

    // --- Bounded external-app session discovery ---------------------------------------------
    fun sessionDiscoveryTopic(deviceId: String) = "homeassistant/sensor/${deviceId}_app_session/config"
    fun sessionStateTopic(deviceId: String) = "portal/$deviceId/session/state"
    fun sessionEventTopic(deviceId: String) = "portal/$deviceId/session/event"
    fun sessionCommandTopic(deviceId: String) = "portal/$deviceId/session/command"
    fun sessionConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"App Session","unique_id":"${deviceId}_app_session","device":${device(deviceId, name)},"state_topic":"${sessionStateTopic(deviceId)}","value_template":"{{ value_json.lifecycle }}","expire_after":15,"icon":"mdi:application-cog","entity_category":"diagnostic"}"""
    }

    fun sessionEnabledDiscoveryTopic(deviceId: String) = "homeassistant/binary_sensor/${deviceId}_app_sessions_enabled/config"
    fun sessionEnabledStateTopic(deviceId: String) = "portal/$deviceId/session/enabled"
    fun sessionEnabledConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"App Sessions Enabled","unique_id":"${deviceId}_app_sessions_enabled","device":${device(deviceId, name)},"state_topic":"${sessionEnabledStateTopic(deviceId)}","payload_on":"ON","payload_off":"OFF","icon":"mdi:shield-lock","entity_category":"diagnostic"}"""
    }

    fun commandTopics(deviceId: String) = listOf(
        screenCommandTopic(deviceId),
        volumeCommandTopic(deviceId),
        volumeMuteCommandTopic(deviceId),
        soundCommandTopic(deviceId),
        brightnessCommandTopic(deviceId),
        screenTimeoutCommandTopic(deviceId),
        screenTimeoutMinutesCommandTopic(deviceId),
        powerModeCommandTopic(deviceId),
        notificationCommandTopic(deviceId),
        sessionCommandTopic(deviceId),
    )

    private fun device(deviceId: String, escapedName: String) =
        """{"identifiers":["$deviceId"],"name":"$escapedName","model":"Meta Portal","manufacturer":"Meta"}"""

    private fun String.escape() = replace("\"", "\\\"")
}
