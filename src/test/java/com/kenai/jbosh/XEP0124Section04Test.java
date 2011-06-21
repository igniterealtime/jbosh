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
 * BOSH XEP-0124 specification section 4 tests: The BOSH Technique.
 */
public class XEP0124Section04Test extends AbstractBOSHTest {

    /*
     * The client SHOULD NOT open more than two HTTP connections to the
     * connection manager at the same time, [12] so it would otherwise have to
     * wait until the connection manager responds to one of the requests.
     */
    @Test
    public void concurrentConnections() {
        // TODO: Test for number of concurrent HTTP connections?
    }

}
