package com.xeno.mupa

import com.xeno.mupa.navigation.MupaTopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MupaNavigationTest {

    @Test
    fun topLevelDestinationsMatchFourIndonesianTabs() {
        val entries = MupaTopLevelDestination.entries
        assertEquals(4, entries.size)
        assertEquals(
            listOf("Beranda", "Cari", "Pustaka", "Akun"),
            entries.map { it.label },
        )
    }

    @Test
    fun topLevelRoutesAreUniqueAndMatchExpectedIndonesianRoutes() {
        val routes = MupaTopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
        assertEquals(listOf("beranda", "cari", "pustaka", "akun"), routes)
        routes.forEach { route ->
            assertTrue(route.isNotBlank())
        }
    }
}
