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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests the {@code AttrVersion} attribute class.
 */
public class AttrAcceptTest {

    @Test
    public void testNullOnNull() throws BOSHException {
        AttrAccept attr = AttrAccept.createFromString(null);
        assertNull(attr);
    }

    @Test
    public void commaDelimiter() {
        try {
            AttrAccept accept = AttrAccept.createFromString("foo,bar");
            assertNotNull("accept was null", accept);
            assertTrue("did not accept foo", accept.isAccepted("foo"));
            assertTrue("did not accept bar", accept.isAccepted("bar"));
        } catch (BOSHException boshx) {
            fail("Caught exception: " + boshx);
        }
    }

    @Test
    public void spaceDelimiter() {
        try {
            AttrAccept accept = AttrAccept.createFromString("foo bar");
            assertNotNull("accept was null", accept);
            assertTrue("did not accept foo", accept.isAccepted("foo"));
            assertTrue("did not accept bar", accept.isAccepted("bar"));
        } catch (BOSHException boshx) {
            fail("Caught exception: " + boshx);
        }
    }

    @Test
    public void multipleDelimiter() {
        try {
            AttrAccept accept = AttrAccept.createFromString("foo, bar");
            assertNotNull("accept was null", accept);
            assertTrue("did not accept foo", accept.isAccepted("foo"));
            assertTrue("did not accept bar", accept.isAccepted("bar"));
        } catch (BOSHException boshx) {
            fail("Caught exception: " + boshx);
        }
    }

    @Test
    public void extraWhitespace() {
        try {
            AttrAccept accept = AttrAccept.createFromString("  foo , bar ");
            assertNotNull("accept was null", accept);
            assertTrue("did not accept foo", accept.isAccepted("foo"));
            assertTrue("did not accept bar", accept.isAccepted("bar"));
        } catch (BOSHException boshx) {
            fail("Caught exception: " + boshx);
        }
    }

}
