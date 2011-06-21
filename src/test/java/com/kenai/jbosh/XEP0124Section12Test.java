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
 * BOSH XEP-0124 specification section 12 tests:  Polling Sessions.
 */
public class XEP0124Section12Test extends AbstractBOSHTest {

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
    public void clientRespondstoRequestsValue() {
        // TODO: client response to 'requests' attribute value of '1' in CM resp
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

    /*
     * If the connection manager did not specify a 'polling' attribute in the
     * session creation response, then it MUST allow the client to poll as
     * frequently as it chooses.
     */
    // BOSH CM functionality not supported.

}
