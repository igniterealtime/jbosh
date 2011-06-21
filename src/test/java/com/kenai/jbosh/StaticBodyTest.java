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

package com.kenai.jbosh;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * BOSH body/wrapper element tests.
 */
public class StaticBodyTest extends BodyTest {

    private static final Logger LOG =
            Logger.getLogger(StaticBodyTest.class.getName());

    public AbstractBody createBody() {
        AbstractBody result;
        try {
            String basicStr = loadResource("BodyTest.basic");
            result = StaticBody.fromString(basicStr);
        } catch (BOSHException boshx) {
            result = null;
        }
        return result;
    }

    @Test
    public void testAttributeParsing() throws BOSHException {
        AbstractBody body = createBody();
        assertNotNull(body);
        assertAttribute(body, BodyQName.createBOSH("foo"), null);
        Map<BodyQName, String> expected = new HashMap<BodyQName, String>();
        expected.put(Attributes.CONTENT, "text/xml; charset=utf-8");
        expected.put(Attributes.HOLD, "1");
        expected.put(Attributes.RID, "1573741820");
        expected.put(Attributes.TO, "server.com");
        expected.put(Attributes.ROUTE, "xmpp:server.com:6222");
        expected.put(Attributes.SECURE, "false");
        expected.put(Attributes.VER, "1.6");
        expected.put(Attributes.WAIT, "60");
        expected.put(Attributes.ACK, "1");
        expected.put(Attributes.XML_LANG, "en");

        Set<BodyQName> attrs = new HashSet<BodyQName>(body.getAttributeNames());
        Iterator<BodyQName> iter = expected.keySet().iterator();
        while (iter.hasNext()) {
            BodyQName name = iter.next();
            assertNotNull(name);
            String expectedVal = expected.get(name);
            assertNotNull(expectedVal);
            assertEquals("Attribute '" + name.getLocalPart()
                    + "' value incorrect", expectedVal,
                    body.getAttribute(name));
            assertTrue(attrs.remove(name));
            iter.remove();
        }
        assertEquals(0, attrs.size());
        assertEquals(0, expected.size());
    }

    @Test
    public void testRawXMLUntouched() throws BOSHException {
        String basicStr = loadResource("BodyTest.basic");
        AbstractBody body = StaticBody.fromString(basicStr);
        assertSame(basicStr, body.toXML());
    }

    //@Test
    public void performanceTest() throws BOSHException {
        String basicStr = loadResource("BodyTest.basic");
        // Warm up the codepath
        int iterations = 100000;
        int warmup = Math.max(1000, iterations / 10);
        for (int i=0; i<warmup; i++) {
            StaticBody.fromString(basicStr);
        }
        // Now time it
        long start = System.currentTimeMillis();
        for (int i=0; i<iterations; i++) {
            StaticBody.fromString(basicStr);
        }
        long end = System.currentTimeMillis();
        long delta = end - start;
        LOG.info("Ran " + iterations + " iterations in " + delta + " ms. ");
        if (delta > 0) {
            LOG.info("    That's " + (iterations * 1000F / delta) + "/s");
        }
    }

}
