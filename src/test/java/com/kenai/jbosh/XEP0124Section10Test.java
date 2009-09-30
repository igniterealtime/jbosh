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

import com.kenai.jbosh.BOSHClient.ExchangeInterceptor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.Test;
import static junit.framework.Assert.*;

/**
 * BOSH XEP-0124 specification section 10 tests:  Inactivity.
 */
public class XEP0124Section10Test extends AbstractBOSHTest {

    private static final Logger LOG = Logger.getLogger(
            XEP0124Section10Test.class.getName());
    /*
     * After receiving a response from the connection manager, if none of the
     * client's requests are still being held by the connection manager (and
     * if the session is not a Polling Session), the client SHOULD make a new
     * request as soon as possible.
     */

    @Test(timeout=5000)
    public void inactiveSessionDelay() throws Exception {
        logTestStart();
        testedBy(ConnectionValidator.class, "scheduleMaxDelayTask");
        final int maxTimeAllowed = 500;

        // Initiate a new session
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(Attributes.INACTIVITY, "1")
                .build();
        conn.sendResponse(scr);
        session.drain();

        // Now wait to see how long before we receive an empty request...
        long start = System.currentTimeMillis();
        cm.awaitConnection();
        long end = System.currentTimeMillis();
        if (end - start > maxTimeAllowed) {
            fail("Idle message took " + (end - start) + "ms to arrive.  "
                    + "Max allowed: " + maxTimeAllowed);
        }

        assertValidators(scr);
    }

    /*
     * If no client requests are being held, the client MUST make a new request
     * before the maximum inactivity period has expired.
     */
    @Test(timeout=5000)
    public void inactiveSession() throws Exception {
        testedBy(ConnectionValidator.class, "scheduleMaxDelayTask");
    }

    /*
     * If the connection manager has responded to all the requests it has
     * received within a session and the time since its last response is longer
     * than the maximum inactivity period, then it SHOULD assume the client has
     * been disconnected and terminate the session without informing the client.
     */
    // BOSH CM functionality not supported.

    /*
     * If the client subsequently makes another request, then the connection
     * manager SHOULD respond as if the session does not exist.
     */
    // BOSH CM functionality not supported.

    /*
     * If the connection manager did not specify a maximum inactivity period
     * in the session creation response, then it SHOULD allow the client to be
     * inactive for as long as it chooses.
     */
    // BOSH CM functionality not supported.

    /*
     * If the session is not a polling session then the connection manager
     * SHOULD specify a relatively short inactivity period to ensure that
     * disconnections are discovered as quickly as possible.
     */
    // BOSH CM functionality not supported.

    /*
     * If a client encounters an exceptional temporary situation during which
     * it will be unable to send requests to the connection manager for a
     * period of time greater than the maximum inactivity period (e.g., while
     * a runtime environment changes from one web page to another), and if the
     * connection manager included a 'maxpause' attribute in its Session
     * Creation Response, then the client MAY request a temporary increase to
     * the maximum inactivity period by including a 'pause' attribute in a
     * request.
     */
    // TODO: Implement provision and implementation of explicit session pausing

    /*
     * If the connection manager did not specify a 'maxpause' attribute at the
     * start of the session then the client MUST NOT send a 'pause' attribute
     * during the session.
     */
    @Test(timeout=5000)
    public void illegalPause() throws Exception {
        logTestStart();
        testedBy(RequestValidator.class, "validateSubsequentPause");
        // Initiate a session with no maxpause
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);

        // Send another request attempting to pause
        session.send(ComposableBody.builder()
                .setAttribute(Attributes.PAUSE, "5")
                .build());
        conn = cm.awaitConnection();
        conn.sendResponse(ComposableBody.builder().build());
        AbstractBody body = conn.getRequest().getBody();
        String val = body.getAttribute(Attributes.PAUSE);
        assertNotNull("pause attribute didn't get to CM", val);

        boolean failed = false;
        try {
            assertValidators(scr);
            failed = true;
        } catch (AssertionError err) {
            LOG.info("Received expected error: " + err.getMessage());
            failed = false;
        }
        if (failed) {
            fail("Did not catch pause attribute");
        }
    }

    /*
     * Upon reception of a session pause request, if the requested period is
     * not greater than the maximum permitted time, then the connection manager
     * SHOULD respond immediately to all pending requests (including the pause
     * request) and temporarily increase the maximum inactivity period to the
     * requested time.
     */
    // BOSH CM functionality not supported.

    /*
     * The response to the pause request MUST NOT contain any payloads.
     */
    // BOSH CM functionality not supported.

    /*
     * If the client simply wants the connection manager to return all the
     * requests it is holding then it MAY set the value of the 'pause'
     * attribute to be the value of the 'inactivity' attribute in the
     * connection manager's session creation response.
     */
    // TODO: Implement a remote flush capability?

    /*
     * If the client believes it is in danger of becoming disconnected
     * indefinitely then it MAY even request a temporary reduction of the
     * maximum inactivity period by specifying a 'pause' value less than the
     * 'inactivity' value, thus enabling the connection manager to discover
     * any subsequent disconnection more quickly.
     */
    // TODO: Implement impending doom pause values?

    /*
     * The connection manager SHOULD set the maximum inactivity period back
     * to normal upon reception of the next request from the client.
     */
    // BOSH CM functionality not supported.

}
