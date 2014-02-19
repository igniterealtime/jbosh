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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.igniterealtime.jbosh.AbstractBody;
import org.igniterealtime.jbosh.Attributes;
import org.igniterealtime.jbosh.BOSHClientConnEvent;
import org.igniterealtime.jbosh.BOSHClientConnListener;
import org.igniterealtime.jbosh.BOSHException;
import org.igniterealtime.jbosh.ComposableBody;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * BOSH XEP-0124 specification section 13 tests: Terminating the HTTP Session.
 */
public class XEP0124Section13Test extends AbstractBOSHTest {

    /**
     * Logger.
     */
    private static final Logger LOG =
            Logger.getLogger(XEP0124Section13Test.class.getName());
    
    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 13:

    /*
     * At any time, the client MAY gracefully terminate the session by sending
     * a <body/> element with a 'type' attribute set to "terminate".
     */

    @Test(timeout=5000)
    public void explicitManualSessionTermination() throws Exception {
        logTestStart();

        final AtomicBoolean connected = new AtomicBoolean();
        session.addBOSHClientConnListener(new BOSHClientConnListener() {
            public void connectionEvent(final BOSHClientConnEvent connEvent) {
                connected.set(connEvent.isConnected());
            }
        });
        
        // Establish a session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        session.drain();

        assertTrue(connected.get());

        // Send an explicit termination
        session.send(ComposableBody.builder()
                .setAttribute(Attributes.SID, scr.getAttribute(Attributes.SID))
                .setAttribute(Attributes.TYPE, "terminate")
                .build());
        conn = cm.awaitConnection();
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.TYPE, "terminate")
                .build());
        session.drain();

        assertFalse(connected.get());

        assertValidators(scr);
    }

    @Test(timeout=5000)
    public void explicitSessionTermination() throws Exception {
        logTestStart();

        final AtomicBoolean connected = new AtomicBoolean();
        session.addBOSHClientConnListener(new BOSHClientConnListener() {
            public void connectionEvent(final BOSHClientConnEvent connEvent) {
                connected.set(connEvent.isConnected());
            }
        });

        // Establish a session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        session.drain();

        assertTrue(connected.get());

        // Send an explicit termination
        session.disconnect();
        conn = cm.awaitConnection();
        AbstractBody req = conn.getRequest().getBody();
        assertEquals("terminate", req.getAttribute(Attributes.TYPE));
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.TYPE, "terminate")
                .build());
        session.drain();
        
        assertFalse(connected.get());

        assertValidators(scr);
    }

    /*
     * The termination request MAY include one or more payloads that the
     * connection manager MUST forward to the server to ensure graceful logoff.
     */
    // BOSH CM functionality not supported.

    /*
     * The connection manager SHOULD return to the client an HTTP 200 OK
     * response with an empty <body/> element.
     */
    // BOSH CM functionality not supported.

    /*
     * Upon receiving the response, the client MUST consider the HTTP session
     * to have been terminated.
     */

    @Test(timeout=5000)
    public void sendAfterTermination() throws Exception {
        logTestStart();

        // Establish a session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        session.drain();

        // Send an explicit termination
        session.disconnect();
        conn = cm.awaitConnection();
        AbstractBody req = conn.getRequest().getBody();
        assertEquals("terminate", req.getAttribute(Attributes.TYPE));
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.TYPE, "terminate")
                .build());
        session.drain();

        // Sending anything else should result in an exception
        try {
            session.send(ComposableBody.builder().build());
            fail("Attempt to send a message after termination succeeded");
        } catch (BOSHException boshx) {
            LOG.info("Received exception: "  +boshx.getMessage());
            // Good!
        }

        assertValidators(scr);
    }

}
