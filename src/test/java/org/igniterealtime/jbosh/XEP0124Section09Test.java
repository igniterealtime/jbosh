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

import java.util.concurrent.atomic.AtomicInteger;

import org.igniterealtime.jbosh.AbstractBody;
import org.igniterealtime.jbosh.Attributes;
import org.igniterealtime.jbosh.CMSessionParams;
import org.igniterealtime.jbosh.ComposableBody;
import org.igniterealtime.jbosh.HTTPExchange;
import org.igniterealtime.jbosh.BOSHClient.ExchangeInterceptor;
import org.junit.Ignore;
import org.junit.Test;

import static junit.framework.Assert.*;

/**
 * BOSH XEP-0124 specification section 9 tests:  Acknowledgements.
 */
public class XEP0124Section09Test extends AbstractBOSHTest {

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 9.1: Request Acknowledgements

    /*
     * When responding to a request that it has been holding, if the connection
     * manager finds it has already received another request with a higher
     * 'rid' attribute (typically while it was holding the first request),
     * then it MAY acknowledge the reception to the client.
     */

    /*
     * The connection manager MAY set the 'ack' attribute of any response to
     * the value of the highest 'rid' attribute it has received in the case
     * where it has also received all requests with lower 'rid' values.
     */

    // BOSH CM functionality not supported.

    @Ignore
    @Test(timeout=5000)
    public void exerciseRequestAck() throws Exception {
        logTestStart();
        StubConnection conn;

        // Session initialization
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        StubRequest scReq = conn.getRequest();
        String rid = scReq.getBody().getAttribute(Attributes.RID);
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.ACK, rid)
                .build();
        conn.sendResponse(scr);
        session.drain();

        // Now send two requests
        session.send(ComposableBody.builder().build());
        StubConnection conn1 = cm.awaitConnection();
        session.send(ComposableBody.builder().build());
        StubConnection conn2 = cm.awaitConnection();

        // Now reply to the first, simulating CM request acks
        AbstractBody req2 = conn1.getRequest().getBody();
        conn1.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.RID, scr.getAttribute(Attributes.RID))
                .setAttribute(
                    Attributes.ACK, req2.getAttribute(Attributes.RID))
                .build());

        // And finally, reply to the second request
        conn2.sendResponse(ComposableBody.builder().build());

        assertValidators(scr);
    }

    /*
     * If the connection manager will be including 'ack' attributes on
     * responses during a session, then it MUST include an 'ack' attribute in
     * its session creation response, and set the 'ack' attribute of responses
     * throughout the session.
     */
    // BOSH CM functionality not supported.
    
    @Test(timeout=5000)
    public void cmWillAck() throws Exception {
        logTestStart();
        StubConnection conn;

        // Session initialization
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        StubRequest scReq = conn.getRequest();
        String rid = scReq.getBody().getAttribute(Attributes.RID);
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.ACK, rid)
                .build();
        conn.sendResponse(scr);
        session.drain();

        CMSessionParams params = session.getCMSessionParams();
        assertNotNull("Session not established", params);
        assertTrue("CM should be using acks", params.isAckingRequests());

        assertValidators(scr);
    }

    @Test(timeout=5000)
    public void cmWillNotAck() throws Exception {
        logTestStart();
        StubConnection conn;

        // Session initialization
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        session.drain();

        CMSessionParams params = session.getCMSessionParams();
        assertNotNull("Session not established", params);
        assertFalse("CM should not be using acks", params.isAckingRequests());

        assertValidators(scr);
    }

    @Test(timeout=5000)
    public void cmWillNotAckBecauseOfBadInitialAckVal() throws Exception {
        logTestStart();
        StubConnection conn;

        // Session initialization
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.ACK, "54321")
                .build();
        conn.sendResponse(scr);
        session.drain();

        CMSessionParams params = session.getCMSessionParams();
        assertNotNull("Session not established", params);
        assertFalse("CM should not be using acks", params.isAckingRequests());

        assertValidators(scr);
    }

    /*
     * After its session creation response, the connection manager SHOULD NOT
     * include an 'ack' attribute in any response if the value would be the
     * 'rid' of the request being responded to.
     */
    // BOSH CM functionality not supported.

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 9.2: Response Acknowledgements

    /*
     * The client MAY similarly inform the connection manager about the
     * responses it has received by setting the 'ack' attribute of any request
     * to the value of the highest 'rid' of a request for which it has already
     * received a response in the case where it has also received all responses
     * associated with lower 'rid' values.
     */
    @Ignore
    @Test(timeout=5000)
    public void clientAcksHighestRID() throws Exception {
        logTestStart();
        final AtomicInteger counter = new AtomicInteger();
        session.setExchangeInterceptor(new ExchangeInterceptor() {
            @Override
            HTTPExchange interceptExchange(HTTPExchange exch) {
                int num = counter.incrementAndGet();
                if (num == 2) {
                    return null;
                } else {
                    return exch;
                }
            }
        });

        // Initiate a session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody body = conn.getRequest().getBody();
        String val = body.getAttribute(Attributes.ACK);
        String initialRID = body.getAttribute(Attributes.RID);
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        assertNotNull("initial ack attribute presence", val);
        assertEquals("initial ack value", "1", val);
        session.drain();

        // Send another request and verify no ack
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .build());
        body = conn.getRequest().getBody();
        val = body.getAttribute(Attributes.ACK);
        assertNull("second request should not have an ack", val);

        // Send another packet and verify ack of initial request only
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .build());
        body = conn.getRequest().getBody();
        val = body.getAttribute(Attributes.ACK);
        assertNotNull("third request should have explicit ack", val);
        assertEquals("ack of initial response", initialRID, val);

        assertValidators(scr);
    }

    /*
     * If the client will be including 'ack' attributes on requests during a
     * session, then it MUST include an 'ack' attribute (set to '1') in its
     * session creation request, and set the 'ack' attribute of requests
     * throughout the session.
     */
    @Test
    public void sessionCreationAck() {
        testedBy(RequestValidator.class, "validateSubsequestRequestAck");
    }

    /*
     * After its session creation request, the client SHOULD NOT include an
     * 'ack' attribute in any request if it has received responses to all its
     * previous requests.
     */
    @Test(timeout=5000)
    public void implicitAck() throws Exception {
        logTestStart();

        // Initiate a session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody body = conn.getRequest().getBody();
        String val = body.getAttribute(Attributes.ACK);
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        assertNotNull("ack presence", val);
        assertEquals("initial ack value", "1", val);
        session.drain();

        // Send another request and verify no ack
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .build());
        body = conn.getRequest().getBody();
        val = body.getAttribute(Attributes.ACK);
        assertNull("second request should have implicit ack", val);

        assertValidators(scr);
    }

    /*
     * After receiving a request with an 'ack' value less than the 'rid' of
     * the last request that it has already responded to, the connection
     * manager MAY inform the client of the situation by sending its next
     * response immediately instead of waiting until it has payloads to send
     * to the client (e.g., if some time has passed since it responded).
     */
    // BOSH CM functionality not supported.

    /*
     * The connection manager SHOULD include a 'report' attribute set to one
     * greater than the 'ack' attribute it received from the client, and a
     * 'time' attribute set to the number of milliseconds since it sent the
     * response associated with the 'report' attribute.
     */
    // BOSH CM functionality not supported.

    /*
     * Upon reception of a response with 'report' and 'time' attributes, if
     * the client has still not received the response associated with the
     * request identifier specified by the 'report' attribute, then it MAY
     * choose to resend the request associated with the missing response (see
     * Broken Connections).
     */
    @Test(timeout=5000)
    public void responseWithAckReport() throws Exception {
        logTestStart();

        // Initiate a session with response acks
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody body = conn.getRequest().getBody();
        String ridStr = body.getAttribute(Attributes.RID);
        long rid = Long.parseLong(ridStr);
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.ACK, ridStr)
                .build();
        conn.sendResponse(scr);
        session.drain();

        // Send another request, acking only the initial RID
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        AbstractBody toRetry = conn.getRequest().getBody();
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.ACK, ridStr)
                .build());
        session.drain();

        // Send another request, but report the second req missing
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.REPORT, Long.toString(rid + 1))
                .setAttribute(Attributes.TIME, "10")
                .build());

        // Expect a retry of the second request
        conn = cm.awaitConnection();
        body = conn.getRequest().getBody();
        String ridRetry = body.getAttribute(Attributes.RID);
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.ACK, Long.toString(rid + 1))
                .build());
        assertEquals("RID was not of retry packet",
                toRetry.getAttribute(Attributes.RID), ridRetry);
        assertEquals("Message should be the same",
                toRetry.toXML(),
                body.toXML());
    }

}
