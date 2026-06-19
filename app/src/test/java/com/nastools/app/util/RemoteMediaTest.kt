package com.nastools.app.util

import com.nastools.app.data.network.RemoteEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMediaTest {

    @Test
    fun typeForName_detectsImagesCaseInsensitively() {
        assertEquals(RemoteMediaType.Image, RemoteMedia.typeForName("Photo.JPG"))
        assertEquals(RemoteMediaType.Image, RemoteMedia.typeForName("scan.heic"))
    }

    @Test
    fun typeForName_detectsVideosCaseInsensitively() {
        assertEquals(RemoteMediaType.Video, RemoteMedia.typeForName("Movie.MP4"))
        assertEquals(RemoteMediaType.Video, RemoteMedia.typeForName("clip.webm"))
    }

    @Test
    fun typeForName_rejectsUnsupportedNames() {
        assertNull(RemoteMedia.typeForName("archive.zip"))
        assertNull(RemoteMedia.typeForName("README"))
    }

    @Test
    fun isSupported_rejectsDirectories() {
        val directory = RemoteEntry(path = "/photos.jpg", name = "photos.jpg", isDirectory = true)

        assertFalse(RemoteMedia.isSupported(directory))
    }

    @Test
    fun isSupported_acceptsMediaFiles() {
        val file = RemoteEntry(path = "/video.mp4", name = "video.mp4", isDirectory = false)

        assertTrue(RemoteMedia.isSupported(file))
    }
}
