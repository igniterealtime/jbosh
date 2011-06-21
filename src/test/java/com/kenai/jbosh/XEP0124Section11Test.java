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
 * BOSH XEP-0124 specification section 11 tests:  Overactivity.
 */
public class XEP0124Section11Test extends AbstractBOSHTest {

    /*
     * The client SHOULD NOT make more simultaneous requests than specified by
     * the 'requests' attribute in the connection manager's Session Creation
     * Response.
     */
    @Test
    public void maxRequestsOutstanding() {
        // TODO: Implement test to ensure the number of outstanding requests
        // never exeeds the maximum.
    }

    /*
     * The client MAY make one additional request if it is to pause or
     * terminate a session.
     */
    // TODO: Test getting to max and then attempting to exceed with a pause
    // or terminate request

    /*
     * If during any period the client sends a sequence of new requests (i.e.
     * requests with incremented rid attributes, not repeat requests) longer
     * than the number specified by the 'requests' attribute, and if the
     * connection manager has not yet responded to any of the requests, and if
     * the last request did not include either a 'pause' attribute or a 'type'
     * attribute set to "terminate", then the connection manager SHOULD
     * consider that the client is making too many simultaneous requests, and
     * terminate the HTTP session with a 'policy-violation' terminal binding
     * error to the client.
     */
    // BOSH CM functionality not supported.
    // TODO: client response to reception of 'policy-violation' binding error

    /*
     * If the connection manager did not specify a 'requests' attribute in the
     * session creation response, then it MUST allow the client to send as
     * many simultaneous requests as it chooses.
     */
    // BOSH CM functionality not supported.

    /*
     * If during any period the client sends a sequence of new requests equal
     * in length to the number specified by the 'requests' attribute, and if
     * the connection manager has not yet responded to any of the requests, and
     * if the last request was empty and did not include either a 'pause'
     * attribute or a 'type' attribute set to "terminate", and if the last two
     * requests arrived within a period shorter than the number of seconds
     * specified by the 'polling' attribute in the session creation response,
     * then the connection manager SHOULD consider that the client is making
     * requests more frequently than it was permitted and terminate the HTTP
     * session and return a 'policy-violation' terminal binding error to the
     * client.
     */
    // BOSH CM functionality not supported.

    /*
     * If the connection manager did not specify a 'polling' attribute in the
     * session creation response, then it MUST allow the client to send
     * requests as frequently as it chooses.
     */
    // BOSH CM functionality not supported.

}
