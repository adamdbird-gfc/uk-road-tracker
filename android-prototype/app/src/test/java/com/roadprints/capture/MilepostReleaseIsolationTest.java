package com.roadprints.capture;

import java.lang.reflect.Modifier;
import org.junit.Test;
import static org.junit.Assert.*;

/** Ordinary debug/JVM classpath must not contain the acceptance source set. */
public class MilepostReleaseIsolationTest {
    @Test public void productionGateIsFinalAndDisabledWithPrivateConstructor()throws Exception {
        assertFalse(MilepostIntegration.PRODUCTION.enabled);
        int flags=MilepostIntegration.class.getDeclaredField("PRODUCTION").getModifiers();
        assertTrue(Modifier.isStatic(flags));assertTrue(Modifier.isFinal(flags));
        for(java.lang.reflect.Constructor<?> c:MilepostIntegration.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(c.getModifiers()));
    }
    @Test public void ordinaryVariantCannotLoadSyntheticEntryPointOrFixture() {
        for(String name:new String[]{"MilepostAcceptanceActivity","MilepostAcceptanceFixture","MilepostAcceptanceChecks","MilepostRuntimeProbe"})
            assertThrows(ClassNotFoundException.class,()->Class.forName("com.roadprints.capture."+name));
    }
}
