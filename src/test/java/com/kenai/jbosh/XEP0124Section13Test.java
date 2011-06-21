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
 * BOSH XEP-0124 specification section 13 tests: Terminating the HTTP Session.
 */
public class XEP0124Section13Test extends AbstractBOSHTest {

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 13:

    /*
     * At any time, the client MAY gracefully terminate the session by sending
     * a <body/> element with a 'type' attribute set to "terminate".
     */
    @Test
    public void explicitSessionTermination() {
        // TODO: Test explicit session termination
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
    // TODO: Test behavior after session has been terminated

}
