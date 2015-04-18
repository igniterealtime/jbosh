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

import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.igniterealtime.jbosh.AbstractBody;
import org.igniterealtime.jbosh.Attributes;
import org.igniterealtime.jbosh.BOSHClient;
import org.igniterealtime.jbosh.BOSHClientConfig;
import org.igniterealtime.jbosh.ComposableBody;
import org.junit.Test;

/**
 * BOSH XEP-0124 specification section 19 tests:  Security Considerations.
 */
public class XEP0124Section19Test extends AbstractBOSHTest {

    private static final Logger LOG =
            Logger.getLogger(XEP0124Section19Test.class.getName());

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 19.1: Connection Between Client and BOSH Service

    /*
     * All communications between a client and a BOSH service SHOULD occur over
     * encrypted HTTP connections.
     */
    // Determined by configured CM URL

    /*
     * Negotiation of encryption between the client and the connection manager
     * SHOULD occur at the transport layer or the HTTP layer, not the
     * application layer.
     */
    // Application considerations are out of slope for this library

    /*
     * Such negotiation SHOULD follow the HTTP/SSL protocol defined in SSL [28],
     * although MAY follow the HTTP/TLS protocol defined in RFC 2818 [29] or
     * the TLS Within HTTP protocol defined in RFC 2817 [30].
     */
    // TODO: Test HTTP/SSL connectivity
    
    // TODO: Test HTTP/TLS connectivity

    /*
     * If the HTTP connection used to send the initial session request is
     * encrypted, then all the other HTTP connections used within the session
     * MUST also be encrypted.
     */
    // TODO: Ensure that mode of encryption does not change over time

    /*
     * If authentication certificates are exchanged when establishing the
     * encrypted connection that is used to send the initial session request,
     * then the client and/or connection manager SHOULD ensure that the same
     * authentication certificates are employed for all subsequent connections
     * used by the session.
     */
    // TODO: Ensure that mode of certificate signing does not change over time

    /*
     * If the connection manager refuses to establish an encrypted connection
     * or offers a different certificate, then the client SHOULD close the
     * connection and terminate the session without sending any more requests.
     */
    // TODO: Figure out how to test certificate comparison

    /*
     * If the client sends a wrapper element that is part of a "secure session"
     * over a connection that either is not encrypted or uses a different
     * certificate, then the connection manager SHOULD simply close the
     * connection. The connection manager SHOULD NOT terminate the session
     * since that would facilitate denial of service attacks.
     */
    // BOSH CM functionality not supported.

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 19.2: Connection Between BOSH Service and Application

    /*
     * A BOSH service SHOULD encrypt its connection to the backend application
     * using appropriate technologies such as Secure Sockets Layer (SSL),
     * Transport Layer Security (TLS), and StartTLS if supported by the
     * backend application.
     */
    // BOSH CM functionality not supported.

    /*
     * If data privacy is desired, the client SHOULD encrypt its messages
     * using an application-specific end-to-end encryption technology.
     */
    // Application considerations are out of slope for this library

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 19.3: Unpredictable SID and RID

    /*
     * The session identifier (SID) and initial request identifier (RID) are
     * security-critical and therefore MUST be both unpredictable and
     * nonrepeating.
     *
     * Note that this is also covered more thoroughly in the current
     * unit tests.  This test is here for redundancy.
     */
    @Test(timeout=120000)
    public void checkForIDRepeats() throws Exception {
        final int iterations = 2500;
        long repeats = 0;
        logTestStart();
        // Run a few thousand iterations and check for repeats
        Set<Long> observed = new HashSet<Long>(iterations * 2);
        BOSHClientConfig cfg = session.getBOSHClientConfig();
        for (int i=0; i<iterations; i++) {
            // Initiate a new session
            BOSHClient sess = BOSHClient.create(cfg);
            sess.send(ComposableBody.builder().build());
            StubConnection conn = cm.awaitConnection();
            AbstractBody req = conn.getRequest().getBody();
            String rid = req.getAttribute(Attributes.RID);
            conn.sendResponse(ComposableBody.builder()
                    .setAttribute(Attributes.TYPE, "terminate")
                    .setAttribute(Attributes.CONDITION, "item-not-found")
                    .build());
            if (!observed.add(Long.valueOf(rid))) {
                repeats++;
            }
        }
        LOG.info("Repeated initial RID " + repeats + " time(s)");
        if (repeats >= 2) {
            fail("Initial RID repeated " + repeats + " times in "
                    + iterations + " iterations");
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 19.4: Use of SHA-1

}
