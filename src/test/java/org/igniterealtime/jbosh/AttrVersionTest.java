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

import org.igniterealtime.jbosh.AttrVersion;
import org.igniterealtime.jbosh.BOSHException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the {@code AttrVersion} attribute class.
 */
public class AttrVersionTest {

    @Test
    public void testNullOnNull() throws BOSHException {
        AttrVersion attr = AttrVersion.createFromString(null);
        assertNull(attr);
    }

    @Test
    public void testExceptionOnUnparseable() {
        try {
            AttrVersion.createFromString("foo bar");
            fail("Should have thrown exception on unparsable input");
        } catch (BOSHException boshx) {
            // Good.
        }
    }

    @Test
    public void testExceptionOnNoMajor() {
        try {
            AttrVersion.createFromString(".0");
            fail("Should have thrown exception on bad major value");
        } catch (BOSHException boshx) {
            // Good.
        }
    }

    @Test
    public void testExceptionOnBadMajor() {
        try {
            AttrVersion.createFromString("-1.0");
            fail("Should have thrown exception on bad major value");
        } catch (BOSHException boshx) {
            // Good.
        }
    }

    @Test
    public void testExceptionOnNoMinor() {
        try {
            AttrVersion.createFromString("0.");
            fail("Should have thrown exception on bad major value");
        } catch (BOSHException boshx) {
            // Good.
        }
    }

    @Test
    public void testExceptionOnBadMinor() {
        try {
            AttrVersion.createFromString("0.-1");
            fail("Should have thrown exception on bad major value");
        } catch (BOSHException boshx) {
            // Good.
        }
    }

    @Test
    public void testVersionExtraction() throws BOSHException {
        AttrVersion attr = AttrVersion.createFromString("15.43");
        assertEquals(15, attr.getMajor());
        assertEquals(43, attr.getMinor());
    }

    @Test
    public void testComparison() throws BOSHException {
        AttrVersion attr1_0 = AttrVersion.createFromString("1.0");
        AttrVersion attr1_0_2 = AttrVersion.createFromString("1.0");
        AttrVersion attr1_10 = AttrVersion.createFromString("1.10");
        AttrVersion attr10_0 = AttrVersion.createFromString("10.0");
        AttrVersion attr1_1 = AttrVersion.createFromString("1.1");
        assertTrue("1.0 == 1.0", attr1_0.compareTo(attr1_0_2) == 0);
        assertTrue("1.0 < 1.10", attr1_0.compareTo(attr1_10) < 0);
        assertTrue("1.10 > 1.0", attr1_10.compareTo(attr1_0) > 0);
        assertTrue("1.10 < 10.0", attr1_10.compareTo(attr10_0) < 0);
        assertTrue("10.0 > 1.10", attr10_0.compareTo(attr1_10) > 0);
        assertTrue("1.1 < 1.10", attr1_1.compareTo(attr1_10) < 0);
    }

}
