package com.nastools.app.presentation.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRoutePathTest {

    @Test
    fun decodeSavedPath_keepsAlreadyDecodedPath() {
        val path = "/photos/100%2Fvalid.jpg"

        assertEquals(path, MediaRoutePath.decodeSavedPath(path))
    }

    @Test
    fun decodeSavedPath_decodesRouteEncodedPath() {
        val path = "%2Fphotos%2F100%252Fvalid.jpg"

        assertEquals("/photos/100%2Fvalid.jpg", MediaRoutePath.decodeSavedPath(path))
    }
}
