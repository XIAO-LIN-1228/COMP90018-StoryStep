package com.comp90018.storystep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Guards the package identity: the application id, the source package and the launcher Activity
 * must all resolve under {@code com.comp90018.storystep}.
 */
@RunWith(AndroidJUnit4.class)
public class ApplicationIdentityTest {

    @Test
    public void applicationIdMatchesTheSourcePackage() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals("com.comp90018.storystep", appContext.getPackageName());
    }

    @Test
    public void launcherActivityClassResolves() throws ClassNotFoundException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertNotNull(appContext.getClassLoader()
                .loadClass("com.comp90018.storystep.ui.MainActivity"));
    }
}
