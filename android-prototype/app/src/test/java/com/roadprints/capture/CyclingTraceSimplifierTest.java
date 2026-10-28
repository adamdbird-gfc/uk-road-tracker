package com.roadprints.capture;
import org.junit.Test;
import static org.junit.Assert.*;
public class CyclingTraceSimplifierTest {
 @Test public void keepsCornersAndClosedLoops() {
  assertArrayEquals(new int[]{0,1,2},CyclingTraceSimplifier.retainedIndices(new double[][]{{0,51},{.001,51},{.001,51.001}}));
  assertArrayEquals(new int[]{0,1,2,3,4},CyclingTraceSimplifier.retainedIndices(new double[][]{{0,51},{.001,51},{.001,51.001},{0,51.001},{0,51}}));
 }
 @Test public void reducesDenseStraightTraceWithoutLosingEndpoints() {
  double[][] points=new double[101][2]; for(int i=0;i<101;i++)points[i]=new double[]{.37+i*.00001,51.44};
  int[] kept=CyclingTraceSimplifier.retainedIndices(points);
  assertEquals(0,kept[0]);assertEquals(100,kept[kept.length-1]);assertTrue(kept.length<101);
 }
 @Test public void retainsIntermediateEvidenceOnLongStraightRoad() {
  double[][] points=new double[101][2]; for(int i=0;i<101;i++)points[i]=new double[]{.37+i*.0001,51.44};
  assertTrue(CyclingTraceSimplifier.retainedIndices(points).length>2);
 }
}
