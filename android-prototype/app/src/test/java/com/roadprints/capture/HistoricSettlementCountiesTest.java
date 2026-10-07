package com.roadprints.capture;

import android.content.Context;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class HistoricSettlementCountiesTest {
    private final Context app = RuntimeEnvironment.getApplication();

    private LocalRoadSettlementMatcher.Settlement mapped(String code, String name) throws Exception {
        LocalRoadSettlementMatcher.Settlement row = new LocalRoadSettlementMatcher.Settlement(code, name);
        HistoricSettlementCounties.align(app, Collections.singletonList(row));
        return row;
    }

    @Test public void entireExistingCatalogueReferencesOnlyTheShared92CountyIds() throws Exception {
        HistoricCountyCatalog counties = new HistoricCountyCatalog(app);
        HistoricSettlementCounties reference = new HistoricSettlementCounties(app);
        assertEquals(counties.version, reference.version);
        assertEquals(8545, reference.settlements.size());
        java.util.Set<String> codes = new HashSet<>();
        for (HistoricCountyCatalog.County county : counties.counties) codes.add(county.code);
        for (HistoricSettlementCounties.Membership row : reference.settlements.values()) {
            assertFalse(row.codes.isEmpty());
            assertEquals(row.codes.size(), new HashSet<>(row.codes).size());
            assertEquals(row.codes.size(), row.names.size());
            assertTrue(codes.containsAll(row.codes));
        }
    }

    @Test public void stableIdsResolveAcrossNationsAndModernCountyNames() throws Exception {
        assertEquals("Kent", HistoricSettlementCounties.displayGroup(mapped("E63005058", "Gravesend")));
        assertEquals("Kent", HistoricSettlementCounties.displayGroup(mapped("E63005204", "Gillingham (Medway)")));
        assertEquals("Lancashire", HistoricSettlementCounties.displayGroup(mapped("E63000792", "Blackpool")));
        assertEquals("Lancashire", HistoricSettlementCounties.displayGroup(mapped("E63001069", "Rochdale")));
        assertEquals("Glamorgan", HistoricSettlementCounties.displayGroup(mapped("W45000596", "Cardiff")));
        assertEquals("Midlothian", HistoricSettlementCounties.displayGroup(mapped("S45000308", "Edinburgh")));
    }

    @Test public void sameNameInDifferentPlacesUsesCodesRatherThanNameAliases() throws Exception {
        LocalRoadSettlementMatcher.Settlement kent = mapped("E63005204", "Gillingham");
        LocalRoadSettlementMatcher.Settlement dorset = mapped("E63006117", "Gillingham");
        LocalRoadSettlementMatcher.Settlement norfolk = mapped("E63002836", "Gillingham");
        assertEquals("Kent", HistoricSettlementCounties.displayGroup(kent));
        assertEquals("Dorset", HistoricSettlementCounties.displayGroup(dorset));
        assertEquals("Norfolk", HistoricSettlementCounties.displayGroup(norfolk));
    }

    @Test public void spanningSettlementRetainsAllCountiesWithoutCreatingNewSettlementIdentity() throws Exception {
        LocalRoadSettlementMatcher.Settlement haverhill = mapped("E63003709", "Haverhill");
        assertEquals("E63003709", haverhill.code);
        assertEquals("Haverhill", haverhill.name);
        assertTrue(haverhill.historicCountyNames.contains("Suffolk"));
        assertTrue(haverhill.historicCountyNames.contains("Essex"));
        assertTrue(haverhill.historicCountyNames.size() > 1);
        assertEquals(haverhill.historicCountyNames.get(0), HistoricSettlementCounties.displayGroup(haverhill));
    }

    @Test public void oldRoadCachesUpgradeWithoutWipingAdministrativeLabelsOrInventories() throws Exception {
        List<JSONObject> geometry = Collections.singletonList(new JSONObject().put("type", "LineString")
                .put("coordinates", new JSONArray("[[0,0],[1,1]]")));
        String key = "old-road:" + Integer.toHexString(geometry.toString().hashCode());
        app.getSharedPreferences("roadprints_local_settlement_matches_v1", Context.MODE_PRIVATE).edit()
                .putString(key, new JSONObject().put("settlements", new JSONArray().put(new JSONObject()
                        .put("code", "E63001069").put("name", "Rochdale")
                        .put("county", "Greater Manchester").put("region", "North West")
                        .put("nation", "England"))).toString()).commit();
        app.getSharedPreferences("roadprints_local_settlement_inventories_v1", Context.MODE_PRIVATE).edit()
                .putString("E63001069", "123").commit();
        List<LocalRoadSettlementMatcher.Settlement> rows = LocalRoadSettlementMatcher.cached(app, "old-road", geometry);
        assertEquals(1, rows.size());
        assertEquals("Greater Manchester", rows.get(0).county);
        assertEquals("North West", rows.get(0).region);
        assertEquals("England", rows.get(0).nation);
        assertEquals("Lancashire", HistoricSettlementCounties.displayGroup(rows.get(0)));
        assertEquals(123, LocalRoadSettlementMatcher.cachedInventoryCount(app, "E63001069"));
        LocalRoadSettlementMatcher.saveCached(app, "old-road", geometry, rows);
        JSONObject stored = new JSONObject(app.getSharedPreferences(
                "roadprints_local_settlement_matches_v1", Context.MODE_PRIVATE).getString(key, "{}"))
                .getJSONArray("settlements").getJSONObject(0);
        assertEquals("LCS", stored.getJSONArray("historic_county_codes").getString(0));
        assertEquals(rows.get(0).historicCountyVersion, stored.getString("historic_county_version"));
        assertEquals(rows.get(0).historicCountyCodes,
                LocalRoadSettlementMatcher.cached(app, "old-road", geometry).get(0).historicCountyCodes);
    }

    @Test public void unknownCodesAndUnrepresentedNorthernIrelandAreExplicitRatherThanGuessed() throws Exception {
        LocalRoadSettlementMatcher.Settlement unknown = new LocalRoadSettlementMatcher.Settlement(
                "N00000001", "Blackpool", "Lancashire", "", "Northern Ireland");
        HistoricSettlementCounties.align(app, Arrays.asList(unknown));
        assertTrue(unknown.historicCountyCodes.isEmpty());
        assertEquals("Historic county not yet mapped", HistoricSettlementCounties.displayGroup(unknown));
        assertEquals("Lancashire", unknown.county);
    }
}
