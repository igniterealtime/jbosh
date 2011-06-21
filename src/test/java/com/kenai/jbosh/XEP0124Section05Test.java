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

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static junit.framework.Assert.*;

/**
 * BOSH XEP-0124 specification section 5 tests:  HTTP Overview.
 */
public class XEP0124Section05Test extends AbstractBOSHTest {

    /*
     * Clients SHOULD send all HTTP requests over a single persistent HTTP/1.1
     * connection using HTTP Pipelining.
     */
    // No mechanism for testing this directly

    /*
     * a client MAY deliver its POST requests in any way permited by RFC 1945
     * or RFC 2616. For example, constrained clients can be expected to open
     * more than one persistent connection instead of using Pipelining, or in
     * some cases to open a new HTTP/1.0 connection to send each request.
     */
    // No mechanism for testing this directly
    @Test
    public void httpMethod() {
        testedBy(ConnectionValidator.class, "assertHTTPMethod");
    }

    /*
     * Clients and connection managers SHOULD NOT use Chunked Transfer Coding.
     */
    @Test
    public void noChunkedEncoding() {
        testedBy(ConnectionValidator.class, "assertChunkedEncoding");
    }

    /*
     * If the connection manager receives a request with an Accept-Encoding
     * header, it MAY include an HTTP Content-Encoding header in the response
     * (indicating one of the encodings specified in the request) and compress
     * the response body accordingly.
     */
    // BOSH CM functionality not supported.

    @Test(timeout=5000)
    public void acceptEncodings() throws Exception {
        logTestStart();
        StubConnection conn;

        // Create a session with compression enabled
        BOSHClientConfig cfg =
                BOSHClientConfig.Builder.create(
                session.getBOSHClientConfig())
                .setCompressionEnabled(true)
                .build();
        session = createSession(cfg);

        // Session initialization
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        StubRequest req = conn.getRequest();
        String accepted = req.getHeader("Accept-Encoding");
        assertNotNull("Request did not allow encodings", accepted);
        conn.sendResponse(scr);
        StubResponse resp = conn.getResponse();
        String encoding = resp.getHeader("Content-Encoding");
        assertNotNull("Response was not encoded", encoding);

        assertValidators(scr);
    }

    /*
     * Requests and responses MAY include HTTP headers not specified herein.
     * The receiver SHOULD ignore any HTTP headers not specified herein.
     */
    @Test(timeout=5000)
    public void responseWithUnknownHeader() throws Exception {
        logTestStart();
        // Creant an artificial response with unknown headers
        session.send(ComposableBody.builder()
                .build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(
                    BodyQName.createBOSH("unknownBOSH"), "val")
                .setAttribute(
                    BodyQName.create("http://unknown.com/", "unknown"), "val")
                .build();
        conn.sendResponse(scr);
        assertValidators(scr);
    }

    /*
     * Each BOSH session MAY share the HTTP connection(s) it uses with other
     * HTTP traffic, including other BOSH sessions and HTTP requests and
     * responses completely unrelated to this protocol.
     */
    // Not implemented

    /*
     * The HTTP Content-Type header of all client requests SHOULD be
     * "text/xml; charset=utf-8".
     */
    @Test
    public void contentType() {
        testedBy(ConnectionValidator.class, "assertContentType");
    }

    /*
     * Clients MAY specify another Content-Type header value if they are
     * constrained to do so.
     */
    // We are not constrained in this manner.  See previous test.

    /*
     * The client and connection manager SHOULD ignore all HTTP Content-Type
     * headers they receive.
     */
    @Test(timeout=5000)
    public void unknownContentTypeHeader() throws Exception {
        logTestStart();
        // Create an artificial response with a random content type
        Map<String,String> headers = new HashMap<String,String>();
        headers.put("Content-type", "foo/bar");

        session.send(ComposableBody.builder()
                .build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .setAttribute(
                    BodyQName.createBOSH("unknownBOSH"), "val")
                .setAttribute(
                    BodyQName.create("http://unknown.com/", "unknown"), "val")
                .build();
        conn.sendResponseWithStatusAndHeaders(scr, 200, headers);

        // We should still be able to use the session
        try {
            session.send(ComposableBody.builder()
                    .build());
            conn = cm.awaitConnection();
            conn.sendResponse(ComposableBody.builder().build());
        } catch (BOSHException boshx) {
            fail("Could not handle unknown Content-type");
        }

        assertValidators(scr);
    }

}
