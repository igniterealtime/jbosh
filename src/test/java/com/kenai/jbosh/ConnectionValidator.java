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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.Assert.*;

/**
 * Validator to test the HTTP aspects of the communication during testing.
 */
public final class ConnectionValidator implements StubCMListener {

    private static final Logger LOG =
            Logger.getLogger(ConnectionValidator.class.getName());

    private final AtomicInteger openConnections = new AtomicInteger();
    private final List<StubConnection> conns =
            new CopyOnWriteArrayList<StubConnection>();
    private final AtomicReference<Error> toThrow =
            new AtomicReference<Error>();
    private final AtomicReference<BOSHClient> client =
            new AtomicReference<BOSHClient>();
    private final AtomicReference<ScheduledFuture> inactivityRef =
            new AtomicReference<ScheduledFuture>();
    private final ScheduledExecutorService schedExec =
            Executors.newSingleThreadScheduledExecutor();

    ///////////////////////////////////////////////////////////////////////////
    // Classes:

    private class InactivityChecker implements Runnable {
        private int max;

        private InactivityChecker(final int maxSecs) {
            max = maxSecs;
        }

        public void run() {
            try {
                fail("Maximum inactivity ("
                        + max + " seconds) expired without a new request");
            } catch (Error err) {
                toThrow.compareAndSet(null, err);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // StubCMListener interface:

    /**
     * {@inheritDoc}
     */
    public void requestReceived(final StubConnection conn) {
        // Disregard any pending inactivity checks
        ScheduledFuture checker = inactivityRef.getAndSet(null);
        if (checker != null) {
            checker.cancel(true);
        }

        conns.add(conn);
        int count = openConnections.incrementAndGet();
        validateConnectionCount(count);
    }

    /**
     * {@inheritDoc}
     */
    public void requestCompleted(final StubConnection conn) {
        int count = openConnections.decrementAndGet();
        try {
            assertTrue("connections >= 0", count >= 0);
        } catch (Error err) {
            toThrow.compareAndSet(null, err);
        }
        scheduleMaxDelayTask(count);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public methods:

    /**
     * Verify that all communications which have transpired have done so
     * according to specification.
     */
    public void checkAssertions() {
        // Disregard any pending checks - we're shutting down
        ScheduledFuture checker = inactivityRef.getAndSet(null);
        if (checker != null) {
            checker.cancel(true);
        }

        Error err = toThrow.get();
        if (err != null) {
            throw(err);
        }
        
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

    ///////////////////////////////////////////////////////////////////////////
    // Package methods:

    /**
     * Set the BOSH session that this validator is monitoring.
     *
     * @param session client session
     */
    void setBOSHClient(final BOSHClient session) {
        client.set(session);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Private methods:

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
        // Only validate connections that we have responses for
        StubResponse resp = conn.getResponse();
        if (resp == null) {
            return;
        }

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

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 10: Inactivity

    /**
     * Validate that the maximum time delta does not expire before a new
     * request is made.
     *
     * "In any case, if no requests are being held, the client MUST make a new
     * request before the maximum inactivity period has expired."
     *
     * @param count current number of outstanding connections
     */
    private void scheduleMaxDelayTask(final int count) {
        if (count > 0) {
            // Nothing to validate
            return;
        }
        try {
            BOSHClient session = client.get();
            if (session == null) {
                // Client not set
                return;
            }
            CMSessionParams params = session.getCMSessionParams();
            if (params == null) {
                // Nothing to validate against
                return;
            }
            AttrInactivity inactivity = params.getInactivityPeriod();
            int max;
            if (inactivity == null) {
                max = 1;
            } else {
                max = inactivity.intValue();
            }
            schedExec.schedule(
                    new InactivityChecker(max), max, TimeUnit.SECONDS);

        } catch (Error err) {
            toThrow.compareAndSet(null, err);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 11: Overactivity

    /**
     * Validate the concurrent connection count.
     *
     * @param count current number of outstanding connections
     */
    private void validateConnectionCount(final int count) {
        try {
            BOSHClient session = client.get();
            int max = 2;
            if (session != null) {
                CMSessionParams params = session.getCMSessionParams();
                if (params == null) {
                    max = 1;
                } else {
                    AttrRequests requests = params.getRequests();
                    if (requests != null) {
                        max = requests.intValue();
                    }
                }
            }
            assertTrue("concurrent connections must be 0 <= x <= " + max
                    + " (was: " + count + ")",
                    count >= 0 && count <= max);
        } catch (Error err) {
            toThrow.compareAndSet(null, err);
        }
    }

}
