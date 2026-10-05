package com.roadprints.capture;

import android.content.Context;
import org.robolectric.RuntimeEnvironment;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class RailStationCatalogTest {
    @Test public void bundledReferenceIncludesTestRouteAndPlatformEnds() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        RailStationCatalog catalog=RailStationCatalog.read(new InputStreamReader(
                context.getAssets().open("rail-stations/stations.csv"),StandardCharsets.UTF_8));
        assertTrue(catalog.stations.size()>2500);
        assertEquals("GRV",catalog.nearest(51.441029,.366990,10).code);
        assertEquals("CHX",catalog.nearest(51.507497,-.123689,10).code);
        assertEquals("STP",catalog.nearest(51.532720,-.127003,10).code);
        assertEquals("STP",catalog.nearest(51.5365,-.1268,10).code);
        assertEquals("SHF",catalog.nearest(53.378371,-1.462138,10).code);
        assertNull(catalog.nearest(51.507497,-.123689,150));
    }
}
