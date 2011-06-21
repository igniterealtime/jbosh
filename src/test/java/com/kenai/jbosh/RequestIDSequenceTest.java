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

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequestIDSequenceTest {

    /**
     * The minimum number of RIDs that must be generated before a repeat
     * is observed.  This is an arbitrary, large value and is not explicitly
     * called out by XEP-0124.
     */
    private static final int ITERATIONS = 150000;

    /**
     * Maximum allowed value that we should never exceed.
     */
    private static final long MAX_ALLOWED = 1L << 53;

    /**
     * Number of times which an RID should reasonable be expected to be
     * incremented by before possibly reaching the max value.
     */
    private static final long MAX_INCREMENTS = 1L << 32;

    private static final Logger LOG =
            Logger.getLogger(RequestIDSequenceTest.class.getName());

    /*
     * The session identifier (SID) and initial request identifier (RID) are
     * security-critical and therefore MUST be both unpredictable and
     * nonrepeating.
     */
    @Test(timeout=20000)
    public void checkForIDRepeats() throws Exception {
        long repeats = 0;
        // Run a few thousand iterations and check for repeats
        Set<Long> observed = new HashSet<Long>(ITERATIONS * 2);
        for (int i=0; i<ITERATIONS; i++) {
            RequestIDSequence seq = new RequestIDSequence();
            Long id = Long.valueOf(seq.getNextRID());
            if (!observed.add(id)) {
                LOG.info("Found a repeat on iteration #" + i + ": " + id);
                repeats++;
            }
        }
        LOG.info("Repeated initial RID " + repeats + " time(s) in "
                + ITERATIONS + " iterations");
        if (repeats > 0) {
            fail("Initial RID repeated " + repeats + " time(s) in "
                    + ITERATIONS + " iterations");
        }
    }

    /*
     * Request IDs must be positive integers, according to the schema, and
     * < 2^53.
     */
    @Test(timeout=30000)
    public void checkRange() throws Exception {
        // Run a few thousand iterations and check for values <= 0
        for (int i=0; i<ITERATIONS; i++) {
            RequestIDSequence seq = new RequestIDSequence();
            long val = seq.getNextRID();
            if (val <= 0) {
                fail("Initial RID repeated value was not a positive integer: "
                        + val);
            }
            if (val > MAX_ALLOWED) {
                fail("Initial RID was greater than the allowed maximum "
                        + "(actual=" + val + ", max=" + MAX_ALLOWED + ")");
            }
            if (val + MAX_INCREMENTS > MAX_ALLOWED) {
                fail ("Initial RID did not leave reasonable room for "
                        + "incrementing on subsequent messages "
                        + "(" + val + " + " + MAX_INCREMENTS + " >= "
                        + MAX_ALLOWED + ")");
            }
        }
    }

}
