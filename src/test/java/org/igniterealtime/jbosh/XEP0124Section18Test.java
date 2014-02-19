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

import org.junit.Test;

/**
 * BOSH XEP-0124 specification section 18 tests: Implementation Notes.
 */
public class XEP0124Section18Test extends AbstractBOSHTest {

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 18.1: HTTP Pipelining

    /*
     * If HTTP Pipelining does not work (because the server returns HTTP 1.0
     * or connection:close), then the client SHOULD degrade gracefully by
     * using multiple connections.
     */

    @Test
    public void notImplemented() {
        // Not implemented
    }
}
