package com.roadprints.capture;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class NationalRailwayCatalogueTest {
    @Test public void packagedCatalogueOpensAndAttachesNorthernIrelandAndGreatBritain() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        try (var catalogue = NationalRailwayCatalogue.open(app, () -> false)) {
            assertStation(catalogue,-5.93973,54.59515,"Belfast Grand Central");
            assertStation(catalogue,-6.66267,55.13396,"Coleraine");
            assertStation(catalogue,-3.179,51.476,"Cardiff Central");
            assertStation(catalogue,-1.462,53.378,"Sheffield");
        }
    }

    private static void assertStation(NationalRailwayCatalogue catalogue,double lon,double lat,String name) {
        var stations = catalogue.stations(RailwayProjection.fix(lon,lat,8,0),550);
        var station = stations.stream().filter(item -> name.equals(item.name())).findFirst();
        assertTrue(name,station.isPresent());
        assertFalse(catalogue.adjacent(station.get().node()).isEmpty());
    }
}
