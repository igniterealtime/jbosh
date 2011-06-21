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

import org.junit.Test;

/**
 * BOSH XEP-0124 specification section 14 tests:  Request IDs.
 */
public class XEP0124Section14Test extends AbstractBOSHTest {

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 14: Request IDs

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 14.1: Generation

    /*
     * The client MUST generate a large, random, positive integer for the
     * initial 'rid' (see Security Considerations) and then increment that
     * value by one for each subsequent request.
     */
    @Test
    public void largeRandomInitialRID() {
        testedBy(RequestValidator.class, "assertSessionCreationRequestID");
    }

    /*
     * The client MUST take care to choose an initial 'rid' that will never be
     * incremented above 9007199254740991 [21] within the session.
     */
    @Test
    public void initialRIDLeavesRoom() {
        testedBy(RequestValidator.class, "assertSessionCreationRequestIDRange");
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 14.2: In-Order Message Forwarding

    /*
     * The connection manager MUST forward the payloads to the server and
     * respond to the client requests in the order specified by the 'rid'
     * attributes.
     */
    // BOSH CM functionality not supported.

    /*
     * The client MUST process responses received from the connection manager
     * in the order the requests were made.
     */
    @Test
    public void outOfOrderResponses() {
        // TODO: Test out or order responses?
    }

    /*
     * The connection manager SHOULD expect the 'rid' attribute to be within a
     * window of values greater than the 'rid' of the previous request.
     * If it receives a request with a 'rid' greater than the values in the
     * window, then the connection manager MUST terminate the session with an
     * error.
     */
    // BOSH CM functionality not supported.

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 14.3: Broken Connections

    /*
     * The connection manager SHOULD remember the 'rid' and the associated
     * HTTP response body of the client's most recent requests which were not
     * session pause requests (see Inactivity) and which did not result in an
     * HTTP or binding error.
     */
    // BOSH CM functionality not supported.

    /*
     * The number of responses to non-pause requests kept in the buffer
     * SHOULD be either the same as the maximum number of simultaneous requests
     * allowed by the connection manager or, if Acknowledgements are being
     * used, the number of responses that have not yet been acknowledged.
     */
    // BOSH CM functionality not supported.

    /*
     * If the network connection is broken or closed before the client receives
     * a response to a request from the connection manager, then the client
     * MAY resend an exact copy of the original request.
     */
    // TODO: Test message resend on transport error

    /*
     * Whenever the connection manager receives a request with a 'rid' that it
     * has already received, it SHOULD return an HTTP 200 (OK) response that
     * includes the buffered copy of the original XML response to the client.
     */
    // BOSH CM functionality not supported.
    // TODO: client handling of duplicated responses

    /*
     * If the original response is not available (e.g., it is no longer in the
     * buffer), then the connection manager MUST return an 'item-not-found'
     * terminal binding error.
     */
    // BOSH CM functionality not supported.
    // TODO: client handling of 'item-not-found' binding error

}
