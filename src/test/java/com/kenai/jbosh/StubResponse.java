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

import org.xlightweb.HttpResponseHeader;

/**
 * Response sent by the stub connection manager.  Used to examine the
 * response for expected conditions during testing.
 */
public class StubResponse {

    private final HttpResponseHeader resp;
    private final AbstractBody body;

    ///////////////////////////////////////////////////////////////////////////
    // Constructor:

    StubResponse(
            final HttpResponseHeader respHead,
            final AbstractBody respBody) {
        if (respHead == null) {
            throw(new IllegalArgumentException(
                    "respHead may not be null"));
        }
        if (respBody == null) {
            throw(new IllegalArgumentException(
                    "respBody may not be null"));
        }
        body = respBody;
        resp = respHead;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public methods:

    public AbstractBody getBody() {
        return body;
    }

    public int getStatus() {
        return resp.getStatus();
    }

    public String getHeader(final String name) {
        return resp.getHeader(name);
    }

}
