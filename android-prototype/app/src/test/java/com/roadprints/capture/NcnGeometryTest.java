package com.roadprints.capture;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NcnGeometryTest {
 private JSONArray line(String s)throws Exception{return new JSONArray(s);}
 @Test public void disconnectedEndpointsAndBranchesStayDisconnected()throws Exception{
  List<JSONArray> lines=Arrays.asList(line("[[0,0],[1,0]]"),line("[[2,0],[1,0]]"),line("[[1,0],[1,1]]"),line("[[2.0001,0],[3,0]]"));assertEquals(4,NcnGeometry.join(lines).size());
 }
 @Test public void sectionInspectionRequiresPositiveActualOverlap()throws Exception{
  JSONArray selected=line("[[0,0],[0.001,0]]");assertTrue(NcnGeometry.overlaps(line("[[0.0001,0],[0.0002,0]]"),selected));assertFalse(NcnGeometry.overlaps(line("[[0,0.00001],[0.001,0.00001]]"),selected));assertFalse(NcnGeometry.overlaps(line("[[0.001,0],[0.002,0]]"),selected));
 }
 @Test public void exactReversedChainAndClosedRingRetainAllVertices()throws Exception{
  List<JSONArray> chain=NcnGeometry.join(Arrays.asList(line("[[0,0],[1,0]]"),line("[[2,0],[1,0]]")));assertEquals(1,chain.size());assertEquals(3,chain.get(0).length());
  List<JSONArray> ring=NcnGeometry.join(Arrays.asList(line("[[0,0],[1,0]]"),line("[[1,0],[1,1]]"),line("[[1,1],[0,0]]")));assertEquals(1,ring.size());assertEquals(4,ring.get(0).length());
 }
}
