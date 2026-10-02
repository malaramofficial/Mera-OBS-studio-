package com.malaramofficial.mobilestudio.data

import com.malaramofficial.mobilestudio.data.local.serializer.SourceConfigSerializer
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceConfigSerializerTest {

    @Test
    fun imageDimensionsSurvivePersistenceRoundTrip() {
        val original = SourceConfig.Image(
            uri = "content://media/external/images/42",
            intrinsicWidthPx = 1080,
            intrinsicHeightPx = 1920
        )

        val restored = SourceConfigSerializer.deserialize(
            SourceConfigSerializer.serialize(original),
            SourceType.IMAGE
        ) as SourceConfig.Image

        assertEquals(original.uri, restored.uri)
        assertEquals(1080, restored.intrinsicWidthPx)
        assertEquals(1920, restored.intrinsicHeightPx)
    }

    @Test
    fun oldImageConfigFormatRemainsReadable() {
        val restored = SourceConfigSerializer.deserialize(
            "IMG|FIT|1.0|content://media/external/images/old",
            SourceType.IMAGE
        ) as SourceConfig.Image

        assertEquals("content://media/external/images/old", restored.uri)
        assertEquals(0, restored.intrinsicWidthPx)
        assertEquals(0, restored.intrinsicHeightPx)
        assertTrue(restored.alpha == 1.0f)
    }
}
