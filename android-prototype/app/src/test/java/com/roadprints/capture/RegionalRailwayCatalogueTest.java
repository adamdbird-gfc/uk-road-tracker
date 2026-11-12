package com.roadprints.capture;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class RegionalRailwayCatalogueTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void independentReferenceThroughAndroidSqlite() throws Exception {
        // Network Rail July 2023 ReferenceLines, HDR asset 9003000554,
        // distance 1,800–3,600 m along the archived engineering geometry.
        List<RailwayMatcher.Fix> fixes = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/railway/hdr-reference.csv"),StandardCharsets.UTF_8))) {
            for (String line; (line = input.readLine()) != null;) {
                String[] fields = line.split(",");
                fixes.add(RailwayProjection.fix(Double.parseDouble(fields[0]),
                        Double.parseDouble(fields[1]),8,Long.parseLong(fields[2])));
            }
        }
        assertEquals(21,fixes.size());
        assertEquals(709892.803,fixes.get(0).x(),.1);
        try (var catalogue = RegionalRailwayCatalogue.open(RuntimeEnvironment.getApplication(),
                temp.newFolder(),()->false)) {
            var result = new RailwayMatcher(catalogue,()->false).match(fixes,0,120000);
            assertTrue(result.toString(),result.complete());
            assertEquals(1800,result.supportedMetres(),50);
        }
    }
}
