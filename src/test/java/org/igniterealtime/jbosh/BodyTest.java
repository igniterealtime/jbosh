/*
 * Copyright 2009 Mike Cumings
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.igniterealtime.jbosh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import org.igniterealtime.jbosh.AbstractBody;
import org.igniterealtime.jbosh.BOSHException;
import org.igniterealtime.jbosh.BodyQName;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * BOSH body/wrapper element tests.
 */
public abstract class BodyTest {

    public abstract AbstractBody createBody();

    @Test
    public void testImmutableMap() throws BOSHException {
        try {
            AbstractBody body = createBody();
            assertNotNull("createBody returned null", body);
            Map<BodyQName, String> map = body.getAttributes();
            map.put(BodyQName.createBOSH("foo"), "bar");
            fail("Attributes map is not write-protected");
        } catch (Throwable thr) {
            // Good.
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // utility methods:

    /**
     * Loads a test resource into Sting form.
     *
     * @param res resource to load
     * @return string
     */
    protected static String loadResource(String res) {
        try {
            ClassLoader loader = BodyTest.class.getClassLoader();
            URL url = loader.getResource(res);
            InputStream stream = url.openStream();
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            int read;
            byte[] buffer = new byte[512];
            do {
                read = stream.read(buffer);
                if (read > 0) {
                    byteOut.write(buffer, 0, read);
                }
            } while(read >= 0);
            return new String(byteOut.toByteArray());
        } catch (IOException iox) {
            throw(new IllegalStateException(
                    "Could not load resource: " + res));
        }
    }

    protected static void assertAttribute(
            final AbstractBody body,
            final BodyQName name,
            final String expected) {
        assertNotNull("Body was null", body);
        assertNotNull("BodyQName was null", name);
        String val = body.getAttribute(name);
        if (expected == null) {
            assertNull("Name (" + name + ") should have been null", val);
        } else {
            assertEquals(
                    "Name (" + name + ") was not '" + expected + "'",
                    expected, val);
        }
    }

}
