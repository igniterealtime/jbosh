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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.Assert.*;

/**
 * Validator to test the HTTP aspects of the communication during testing.
 */
public final class ConnectionValidator {

    private static final Logger LOG =
            Logger.getLogger(ConnectionValidator.class.getName());

    /**
     * Prevent construction.
     */
    private ConnectionValidator() {
        // Empty
    }
    
    /**
     * Verify that all communications which have transpired have done so
     * according to specification.
     *
     * @param cm stub connection manager to evaluate
     */
    public static void checkAssertions(final StubCM cm) {
        List<StubConnection> conns = cm.getConnections();
        if (conns.size() == 0) {
            // Nothing to validate.
            LOG.fine("Nothing to validate");
            return;
        }
        LOG.fine("Validating " + conns.size() + " connections(s)");
        AtomicReference<String> accept = new AtomicReference<String>();
        int index = 0;
        for (StubConnection conn : conns) {
            LOG.fine("Validating connection #" + index);
            validateConnection(index, conn, accept);
            index++;
        }
    }

    /**
     * Validate a connection.
     *
     * @param message number (zero-based)
     * @param conn connectionto validate
     */
    private static void validateConnection(
            final int idx,
            final StubConnection conn,
            final AtomicReference<String> accept) {
        try {
            assertChunkedEncoding(conn);
            assertHTTPMethod(conn);
            assertContentType(conn);
            assertRequestContentEncoding(conn, accept);
        } catch (AssertionError err) {
            LOG.info("Assertion failed for connection #" + idx + ": "
                    + err.getMessage());
            throw(err);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 5: HTTP Overview

    /*
     * XEP-0124 Section 5: Clients and connection managers SHOULD NOT use
     * Chunked Transfer Coding.
     */
    private static void assertChunkedEncoding(final StubConnection conn) {
        StubRequest req = conn.getRequest();
        assertNotNull(req);
        String encoding = req.getHeader("Transfer-Encoding");
        if (encoding == null) {
            // No encoding.  That's fine.
            return;
        }
        assertFalse("Used chunked encoding", encoding.contains("chunked"));
    }

    /**
     * XEP-0124 Section 5: Requests are made using HTTP POST.
     *
     * @param conn connection to verify
     */
    private static void assertHTTPMethod(final StubConnection conn) {
        StubRequest req = conn.getRequest();
        assertNotNull(req);
        String method = req.getMethod();
        assertNotNull(method);
        assertEquals("Incorrect HTTP method", "POST", method);
    }

    /**
     * XEP-0124 Section 5: The HTTP Content-Type header of all client
     * requests SHOULD be "text/xml; charset=utf-8".
     *
     * @param conn connection to verify
     */
    private static void assertContentType(final StubConnection conn) {
        StubRequest req = conn.getRequest();
        assertEquals("Incorrect Content-Type header in request",
                "text/xml; charset=utf-8",
                req.getHeader("Content-Type"));
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 7.2: Session Creation Response

    /*
     * After receiving a session creation response with an 'accept' attribute,
     * clients MAY include an HTTP Content-Encoding header in subsequent
     * requests (indicating one of the encodings specified in the 'accept'
     * attribute) and compress the bodies of the requests accordingly.
     */
    private static void assertRequestContentEncoding(
            final StubConnection conn,
            final AtomicReference<String> accept) {
        StubRequest req = conn.getRequest();
        String encoding = req.getHeader("Content-encoding");
        String accepted = accept.get();
        if (encoding == null) {
            // We allow lack of encoding even if the CM accepts them
        } else if (accepted == null) {
            fail("Request used content encoding '"
                    + encoding + "' prior to response containing an 'accept' "
                    + " attribute");
        } else {
            // Ensure we used one of the accepted encodings
            boolean found = false;
            for (String token : accepted.split("[,\\s]+")) {
                if (encoding.equals(token)) {
                    found = true;
                }
            }
            if (!found) {
                fail("Content encoding '" + encoding + "' was used when the "
                        + "CM only accepts '" + accepted + "'");
            }
        }

        StubResponse resp = conn.getResponse();
        AbstractBody body = resp.getBody();
        String nowAccepts = body.getAttribute(Attributes.ACCEPT);
        if (nowAccepts != null) {
            accept.set(nowAccepts);
        }
    }

}
