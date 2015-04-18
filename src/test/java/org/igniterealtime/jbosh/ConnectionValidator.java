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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.igniterealtime.jbosh.AbstractBody;
import org.igniterealtime.jbosh.AttrInactivity;
import org.igniterealtime.jbosh.AttrPolling;
import org.igniterealtime.jbosh.AttrRequests;
import org.igniterealtime.jbosh.Attributes;
import org.igniterealtime.jbosh.BOSHClient;
import org.igniterealtime.jbosh.BOSHException;
import org.igniterealtime.jbosh.CMSessionParams;
import org.igniterealtime.jbosh.ComposableBody;
import org.igniterealtime.jbosh.StaticBody;

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
    private final AtomicReference<ScheduledFuture<?>> inactivityRef =
            new AtomicReference<ScheduledFuture<?>>();
    private final ScheduledExecutorService schedExec =
            Executors.newSingleThreadScheduledExecutor();

    ///////////////////////////////////////////////////////////////////////////
    // Classes:

    private class InactivityChecker implements Runnable {
        private final int max;

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
        cancelInactivityCheck();

        conns.add(conn);
        int count = openConnections.incrementAndGet();
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest("CM connections outstanding: " + count);
        }
        validateConnectionCount(count, conn);
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
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest("CM connections outstanding: " + count);
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
        done();
        
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
        StubConnection prev = null;
        for (StubConnection conn : conns) {
            LOG.fine("Validating connection #" + index);
            validateConnection(index, conn, accept);
            validateOveractivePolling(index, conn, prev);
            prev = conn;
            index++;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Package methods:

    /**
     * Mark the end of the tests.
     */
    void done() {
        cancelInactivityCheck();
    }

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
            LOG.finest("Scheduling inactivity timer");
            inactivityRef.set(schedExec.schedule(
                    new InactivityChecker(max), max, TimeUnit.SECONDS));
        } catch (Error err) {
            toThrow.compareAndSet(null, err);
        }
    }

    /**
     * Cancel any outstanding activity timer check.
     */
    private void cancelInactivityCheck() {
        // Disregard any pending checks - we're shutting down
        ScheduledFuture<?> checker = inactivityRef.getAndSet(null);
        if (checker != null) {
            LOG.finest("Inactivity timer canceled");
            checker.cancel(true);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 11: Overactivity

    /**
     * Validate the concurrent connection count.
     *
     * @param count current number of outstanding connections
     * @param conn current connection
     */
    private void validateConnectionCount(
            final int count, final StubConnection conn) {
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

            AbstractBody msg = conn.getRequest().getBody();
            int extra;
            if (msg.getAttribute(Attributes.PAUSE) != null
                    || "terminate".equals(msg.getAttribute(Attributes.TYPE))) {
                extra = 1;
            } else {
                extra = 0;
            }

            assertTrue("concurrent connections must be 0 <= x <= " + max
                    + "+" + extra + " (was: " + count + ")",
                    count >= 0 && count <= (max + extra));
        } catch (Error err) {
            toThrow.compareAndSet(null, err);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 12: Polling Sessions

    /**
     * If the client sends two consecutive empty new requests (i.e. requests
     * with incremented rid attributes, not repeat requests) within a period
     * shorter than the number of seconds specified by the 'polling' attribute
     * (the shortest allowable polling interval) in the session creation
     * response, and if the connection manager's response to the first request
     * contained no payloads, then upon reception of the second request the
     * connection manager SHOULD terminate the HTTP session and return a
     * 'policy-violation' terminal binding error to the client.
     *
     * @param index connection index
     * @param conn current connection
     * @pram previous previous connection
     */
    private void validateOveractivePolling(
            final int index,
            final StubConnection conn,
            final StubConnection previous) {
        BOSHClient session = client.get();
        if (session == null || previous == null) {
            // No established session
            return;
        }

        ComposableBody prevReq =
                toComposableBody(previous.getRequest().getBody());
        ComposableBody req =
                toComposableBody(conn.getRequest().getBody());
        String prevIDStr = prevReq.getAttribute(Attributes.RID);
        String idStr = req.getAttribute(Attributes.RID);
        long prevID = Long.parseLong(prevIDStr);
        long id = Long.parseLong(idStr);
        if (!(prevReq.getPayloadXML().isEmpty()
                && req.getPayloadXML().isEmpty())
                && (id - prevID != 1)) {
            // Not two consecutive empty requests
            return;
        }

        ComposableBody prevResp =
                toComposableBody(previous.getResponse().getBody());
        if (!prevResp.getPayloadXML().isEmpty()) {
            // Previous response was not empty
            return;
        }

        CMSessionParams params = session.getCMSessionParams();
        if (params == null) {
            // Nothing to validate against
            return;
        }

        AttrPolling polling = params.getPollingInterval();
        if (polling == null) {
            // Nothing to check against
            return;
        }

        long prevTime = previous.getRequest().getRequestTime();
        long connTime = conn.getRequest().getRequestTime();
        long delta = connTime - prevTime;
        if (delta < polling.getInMilliseconds() ) {
            fail("Polling session overactivity policy violation in "
                    + "connection #" + index + " (" + delta + " < "
                    + polling.getInMilliseconds() + ")");
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    /**
     * Convert the abstract body supplied into a composable body instance
     * as best we can.
     *
     * @param body body to convert
     * @return composable body instance
     */
    private static ComposableBody toComposableBody(final AbstractBody body) {
        if (body instanceof ComposableBody) {
            return (ComposableBody) body;
        }
        try {
            StaticBody sBody = StaticBody.fromString(body.toXML());
            return ComposableBody.fromStaticBody(sBody);
        } catch (BOSHException boshx) {
            fail("Could not convert body to static body: " + body);
        }
        throw(new IllegalStateException("Shouldn't get here"));
    }

}
