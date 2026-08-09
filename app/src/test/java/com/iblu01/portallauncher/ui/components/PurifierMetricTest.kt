package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.domain.model.PillDetail
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PurifierMetricTest {
    @Test fun `classifies pollutant thresholds and filter health in the right direction`() {
        assertEquals(PurifierQuality.GOOD, purifierQuality(PurifierMetricKind.CO2, 620f))
        assertEquals(PurifierQuality.FAIR, purifierQuality(PurifierMetricKind.CO2, 900f))
        assertEquals(PurifierQuality.POOR, purifierQuality(PurifierMetricKind.CO2, 1_200f))
        assertEquals(PurifierQuality.GOOD, purifierQuality(PurifierMetricKind.PM25, 4f))
        assertEquals(PurifierQuality.POOR, purifierQuality(PurifierMetricKind.PM25, 30f))
        assertEquals(PurifierQuality.GOOD, purifierQuality(PurifierMetricKind.FILTER, 82f))
        assertEquals(PurifierQuality.POOR, purifierQuality(PurifierMetricKind.FILTER, 12f))
    }

    @Test fun `uses live Home Assistant metadata to build a display metric`() {
        val entity = HaEntity(
            "sensor.purifier_co2",
            "620",
            JSONObject().put("device_class", "carbon_dioxide").put("unit_of_measurement", "ppm"),
        )
        val metric = purifierMetric(PillDetail("CO₂", "620ppm", entity.entityId), entity)

        assertEquals(PurifierMetricKind.CO2, metric.kind)
        assertEquals("620", metric.displayValue)
        assertEquals("ppm", metric.unit)
        assertEquals(PurifierQuality.GOOD, metric.quality)
    }
}
