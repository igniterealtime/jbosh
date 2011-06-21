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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.junit.Test;
import static junit.framework.Assert.*;

/**
 * BOSH XEP-0124 specification section 12 tests:  Polling Sessions.
 */
public class XEP0124Section12Test extends AbstractBOSHTest {

    private static final Logger LOG =
            Logger.getLogger(XEP0124Section12Test.class.getName());

    /*
     * If it is not possible for a constrained client to either use HTTP
     * Pipelining or open more than one HTTP connection with the connection
     * manager at a time, the client SHOULD inform the connection manager by
     * setting the values of the 'wait' and/or 'hold' attributes in its
     * session creation request to "0", and then "poll" the connection manager
     * at regular intervals throughout the session for payloads it might have
     * received from the server.
     */
    // We are not constrained in this manner.

    /*
     * Even if the client does not request a polling session, the connection
     * manager MAY require a client to use polling by setting the 'requests'
     * attribute (which specifies the number of simultaneous requests the
     * client can make) of its Session Creation Response to "1".
     */
    // BOSH CM functionality not supported.

    @Test
    public void requestBlocksWhenPollResponseOutstanding() throws Exception {
        logTestStart();
        // Client response to 'requests' attribute value of '1' in CM resp
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.REQUESTS, "1")
                .build();
        conn.sendResponse(scr);
        session.drain();

        // Verify the CM params got picked up correctly
        CMSessionParams params = session.getCMSessionParams();
        AttrRequests aReq = params.getRequests();
        assertNotNull(aReq);
        assertEquals(1, aReq.intValue());

        // Establish the one allowed connection
        final ComposableBody resp = ComposableBody.builder()
            .setAttribute(Attributes.SID, scr.getAttribute(Attributes.SID))
            .build();
        session.send(ComposableBody.builder()
                .setNamespaceDefinition("foo", "http://127.0.0.1/")
                .setPayloadXML("<foo:bar/>")
                .build());
        StubConnection conn1 = cm.awaitConnection();

        // Set up a thread to send another request
        final AtomicBoolean completed = new AtomicBoolean();
        final Thread thr = new Thread(new Runnable() {
            public void run() {
                try {
                    session.send(ComposableBody.builder().build());
                    StubConnection conn2 = cm.awaitConnection();
                    conn2.sendResponse(resp);
                } catch (Throwable thr) {
                    // Ignore.  It will register as a failure anyhow.
                } finally {
                    completed.set(true);
                }
            }
        });

        // Run the other thread and make sure it blocks
        boolean pass = false;
        try {
            thr.start();
            try {
                // If we havent completed after 1.5 seconds, that's good
                Thread.sleep(1500);
                pass = thr.isAlive();
            } catch (InterruptedException intx) {
                // Not expected
            }
        } finally {
            thr.interrupt();
        }

        // Cleanup
        conn1.sendResponse(resp);

        assertTrue("Did not block until response was given", pass);
        assertValidators(scr);
    }

    /*
     * If a session will use polling, the connection manager SHOULD specify a
     * higher than normal value for the 'inactivity' attribute (see Inactivity)
     * in its session creation response.  The increase SHOULD be greater than
     * the value it specifies for the 'polling' attribute.
     */
    // BOSH CM functionality not supported.

    /*
     * If the client sends two consecutive empty new requests (i.e. requests
     * with incremented rid attributes, not repeat requests) within a period
     * shorter than the number of seconds specified by the 'polling' attribute
     * (the shortest allowable polling interval) in the session creation
     * response, and if the connection manager's response to the first request
     * contained no payloads, then upon reception of the second request the
     * connection manager SHOULD terminate the HTTP session and return a
     * 'policy-violation' terminal binding error to the client.
     */
    // BOSH CM functionality not supported.

    @Test
    public void verifyOveractivePolling() throws Exception {
        logTestStart();
        testedBy(ConnectionValidator.class, "validateOveractivePolling");

        // Client response to 'requests' attribute value of '1' in CM resp
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.REQUESTS, "1")
                .setAttribute(Attributes.POLLING, "1")
                .build();
        conn.sendResponse(scr);
        session.drain();

        // Two consecutive, empty requests
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, scr.getAttribute(Attributes.SID))
                .build());
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, scr.getAttribute(Attributes.SID))
                .build());
        session.drain();

        boolean pass = true;
        try {
            assertValidators(scr);
            pass = false;
        } catch (Error err) {
            // Good, we caught it!
            LOG.info("Caught assertion failure: " + err.getMessage());
        }
        if (!pass) {
            fail("did not catch overactive polling");
        }
    }

    @Test(timeout=5000)
    public void overactivePollingCausedByIdle() throws Exception {
        logTestStart();
        testedBy(ConnectionValidator.class, "validateOveractivePolling");

        // Client response to 'requests' attribute value of '1' in CM resp
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.REQUESTS, "1")
                .setAttribute(Attributes.POLLING, "3")
                .setAttribute(Attributes.INACTIVITY, "4")
                .setNamespaceDefinition("foo", "http://127.0.0.1/")
                .setPayloadXML("<foo:bar/>")
                .build();
        conn.sendResponse(scr);
        session.drain();

        // Send one empty request
        session.send(ComposableBody.builder().build());
        StubConnection conn1 = cm.awaitConnection();
        conn1.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, scr.getAttribute(Attributes.SID))
                .build());

        // Now wait for the empty request caused by idling
        StubConnection conn2 = cm.awaitConnection();
        conn2.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, scr.getAttribute(Attributes.SID))
                .build());

        assertValidators(scr);
    }

    /*
     * If the connection manager did not specify a 'polling' attribute in the
     * session creation response, then it MUST allow the client to poll as
     * frequently as it chooses.
     */
    // BOSH CM functionality not supported.

}
