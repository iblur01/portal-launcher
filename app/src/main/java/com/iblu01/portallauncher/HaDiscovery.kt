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

    fun accelDiscoveryTopic(deviceId: String, axis: String) = "homeassistant/sensor/${deviceId}_accel_$axis/config"
    fun accelStateTopic(deviceId: String) = "portal/$deviceId/sensor/accelerometer"
    fun accelConfigPayload(deviceId: String, deviceName: String, axis: String): String {
        val name = deviceName.escape()
        return """{"name":"Accel ${axis.uppercase()}","unique_id":"${deviceId}_accel_$axis","device":${device(deviceId, name)},"state_topic":"${accelStateTopic(deviceId)}","value_template":"{{ value_json.$axis }}","unit_of_measurement":"m/s²","state_class":"measurement"}"""
    }

    fun rgbDiscoveryTopic(deviceId: String, channel: String) = "homeassistant/sensor/${deviceId}_rgb_$channel/config"
    fun rgbStateTopic(deviceId: String) = "portal/$deviceId/sensor/rgb"
    fun rgbConfigPayload(deviceId: String, deviceName: String, channel: String): String {
        val name = deviceName.escape()
        return """{"name":"Light ${channel.uppercase()}","unique_id":"${deviceId}_rgb_$channel","device":${device(deviceId, name)},"state_topic":"${rgbStateTopic(deviceId)}","value_template":"{{ value_json.$channel }}","unit_of_measurement":"lx","state_class":"measurement","icon":"mdi:palette"}"""
    }

    fun soundDiscoveryTopic(deviceId: String) = "homeassistant/sensor/${deviceId}_sound/config"
    fun soundStateTopic(deviceId: String) = "portal/$deviceId/sensor/sound"
    fun soundConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Sound Level","unique_id":"${deviceId}_sound","device":${device(deviceId, name)},"state_topic":"${soundStateTopic(deviceId)}","unit_of_measurement":"%","state_class":"measurement","icon":"mdi:microphone"}"""
    }

    fun micMuteDiscoveryTopic(deviceId: String) = "homeassistant/switch/${deviceId}_mic_mute/config"
    fun micMuteStateTopic(deviceId: String) = "portal/$deviceId/mic/mute/state"
    fun micMuteCommandTopic(deviceId: String) = "portal/$deviceId/mic/mute/set"
    fun micMuteConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"Mic Mute","unique_id":"${deviceId}_mic_mute","device":${device(deviceId, name)},"state_topic":"${micMuteStateTopic(deviceId)}","command_topic":"${micMuteCommandTopic(deviceId)}","payload_on":"ON","payload_off":"OFF","state_on":"ON","state_off":"OFF","icon":"mdi:microphone-off"}"""
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

    fun ipDiscoveryTopic(deviceId: String) = "homeassistant/sensor/${deviceId}_ip/config"
    fun ipStateTopic(deviceId: String) = "portal/$deviceId/sensor/ip"
    fun ipConfigPayload(deviceId: String, deviceName: String): String {
        val name = deviceName.escape()
        return """{"name":"IP Address","unique_id":"${deviceId}_ip","device":${device(deviceId, name)},"state_topic":"${ipStateTopic(deviceId)}","icon":"mdi:ip-network","entity_category":"diagnostic"}"""
    }

    fun commandTopics(deviceId: String) = listOf(
        screenCommandTopic(deviceId),
        micMuteCommandTopic(deviceId),
        volumeCommandTopic(deviceId),
        volumeMuteCommandTopic(deviceId),
        soundCommandTopic(deviceId),
        brightnessCommandTopic(deviceId),
        screenTimeoutCommandTopic(deviceId),
        screenTimeoutMinutesCommandTopic(deviceId),
        powerModeCommandTopic(deviceId),
        notificationCommandTopic(deviceId)
    )

    fun staleTopics(deviceId: String) = listOf(
        accelDiscoveryTopic(deviceId, "x"),
        accelDiscoveryTopic(deviceId, "y"),
        accelDiscoveryTopic(deviceId, "z"),
        rgbDiscoveryTopic(deviceId, "r"),
        rgbDiscoveryTopic(deviceId, "g"),
        rgbDiscoveryTopic(deviceId, "b"),
    )

    private fun device(deviceId: String, escapedName: String) =
        """{"identifiers":["$deviceId"],"name":"$escapedName","model":"Meta Portal","manufacturer":"Meta"}"""

    private fun String.escape() = replace("\"", "\\\"")
}
