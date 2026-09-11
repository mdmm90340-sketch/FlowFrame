package com.flowframe.app.data

import org.junit.Assert.*
import org.junit.Test

class NetworkStateTest {
    @Test fun wifiRestrictionDoesNotTreatUnmeteredCellularOrEthernetAsWifi() {
        assertFalse(NetworkState(connected = true, wifi = false).permitsDownload(true))
        assertTrue(NetworkState(connected = true, wifi = false).permitsDownload(false))
    }
    @Test fun wifiWithoutValidatedInternetDoesNotStartDownloads() {
        assertFalse(NetworkState(connected = false, wifi = true).permitsDownload(true))
        assertTrue(NetworkState(connected = true, wifi = true).permitsDownload(true))
    }
}
