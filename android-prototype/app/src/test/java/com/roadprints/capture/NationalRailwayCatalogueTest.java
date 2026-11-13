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

    @Test public void denseRoadCellsKeepTheBoundForNearbySegments() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        var file = java.io.File.createTempFile("national-road-bounds", ".sqlite", app.getCacheDir());
        try {
            try (var db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)) {
                db.execSQL("PRAGMA user_version=1");
                db.execSQL("CREATE TABLE road(id INTEGER PRIMARY KEY, ax REAL, ay REAL, bx REAL, by REAL)");
                db.execSQL("CREATE TABLE road_cell(id INTEGER, x INTEGER, y INTEGER)");
                db.execSQL("WITH RECURSIVE ids(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM ids WHERE n<257)"
                        + " INSERT INTO road SELECT n, 100, 100, 110, 110 FROM ids");
                db.execSQL("INSERT INTO road VALUES (258, 0, 4, 10, 4)");
                db.execSQL("INSERT INTO road_cell SELECT id, 0, 0 FROM road");
            }
            try (var catalogue = new NationalRailwayCatalogue(file, "")) {
                assertEquals(4, catalogue.roadResidual(new RailwayMatcher.Fix(5, 0, 8, 0)), 0);
                assertThrows(RegionalRailwayCatalogue.QueryLimit.class,
                        () -> catalogue.roadResidual(new RailwayMatcher.Fix(105, 105, 8, 0)));
            }
        } finally {
            java.nio.file.Files.deleteIfExists(file.toPath());
        }
    }

    private static void assertStation(NationalRailwayCatalogue catalogue,double lon,double lat,String name) {
        var stations = catalogue.stations(RailwayProjection.fix(lon,lat,8,0),550);
        var station = stations.stream().filter(item -> name.equals(item.name())).findFirst();
        assertTrue(name,station.isPresent());
        assertFalse(catalogue.adjacent(station.get().node()).isEmpty());
    }
}
