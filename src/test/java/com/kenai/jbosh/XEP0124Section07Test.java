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
import static junit.framework.Assert.*;

/**
 * BOSH XEP-0124 specification section 7 tests:  Initiating a BOSH Session.
 */
public class XEP0124Section07Test extends AbstractBOSHTest {

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 7.1: Session Creation Request

    /*
     * The <body/> element of the first request SHOULD possess the following
     * attributes (they SHOULD NOT be included in any other requests except as
     * specified under Adding Streams To A Session): "to", "xml:lang", "ver",
     * "wait", "hold".
     */
    @Test
    public void sessionCreationRequiredAttributes() {
        testedBy(RequestValidator.class, "validateRequestHeaders");
    }

    /*
     * The "ver" attribute numbering scheme is "<major>.<minor>" (where the
     * minor number MAY be incremented higher than a single digit, so it MUST
     * be treated as a separate integer).
     */
    @Test
    public void versionMajorMinorParsing() {
        testedBy(AttrVersionTest.class, "testComparison");
    }

    /*
     * If the client is not able to use HTTP Pipelining then the "hold"
     * attribute SHOULD be set to "1".
     */
    @Test
    public void sessionCreationHold() {
        testedBy(RequestValidator.class, "validateSessionCreationHold");
    }

    /*
     * Clients that only support Polling Sessions MAY prevent the connection
     * manager from waiting by setting 'wait' or 'hold' to "0".
     */
    // Tested in Section 12

    /*
     * A connection manager MAY be configured to enable sessions with more than
     * one server in different domains. When requesting a session with such a
     * "proxy" connection manager, a client SHOULD include a 'route' attribute
     * that specifies the protocol, hostname, and port of the server with which
     * it wants to communicate, formatted as "proto:host:port" (e.g.,
     * "xmpp:jabber.org:9999"). [18]
     */
    @Test(timeout=5000)
    public void specifyRoute() throws Exception {
        logTestStart();

        // Create a session with route specified
        BOSHClientConfig cfg =
                BOSHClientConfig.Builder.create(
                session.getBOSHClientConfig())
                .setRoute("xmpp", "jabber.org", 9999)
                .build();
        session = createSession(cfg);

        // Attempt to send a body containing processing instruction
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);

        // Check the request to make sure the route was specified
        StubRequest req = conn.getRequest();
        String routeStr = req.getBody().getAttribute(Attributes.ROUTE);
        assertNotNull("route attribute was null", routeStr);
        assertEquals("incorrectly formatted route attribute",
                "xmpp:jabber.org:9999", routeStr);

        assertValidators(scr);
    }

    /*
     * A client MAY include a 'from' attribute to enable the connection manager
     * to forward its identity to the server.
     */
    @Test(timeout=5000)
    public void specifyFrom() throws Exception {
        logTestStart();

        // Create a session with route specified
        BOSHClientConfig cfg =
                BOSHClientConfig.Builder.create(
                session.getBOSHClientConfig())
                .setFrom("me2you")
                .build();
        session = createSession(cfg);

        // Attempt to send a body containing processing instruction
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);

        // Check the request to make sure the route was specified
        StubRequest req = conn.getRequest();
        String fromStr = req.getBody().getAttribute(Attributes.FROM);
        assertNotNull("from attribute was null", fromStr);
        assertEquals("incorrect from attribute value", "me2you", fromStr);

        assertValidators(scr);
    }

    /*
     * A client MAY include an 'ack' attribute (set to "1") to indicate that
     * it will be using acknowledgements throughout the session and that the
     * absence of an 'ack' attribute in any request is meaningful (see
     * Acknowledgements).
     */
    @Test
    public void sessionCreationAck() {
        testedBy(RequestValidator.class, "validateSessionCreationAck");
    }

    /*
     * The <body/> element of the first request MAY possess a 'content'
     * attribute.
     */
    // The content attribute is for constrained clients.  We are not
    // so constrained.

    /*
     * All requests after the first one MUST include a valid 'sid' attribute
     * (provided by the connection manager in the Session Creation Response).
     */
    @Test
    public void subsequentSessionIDs() {
        testedBy(RequestValidator.class, "validateSubsequentRequestSID");
    }

    /*
     * The initialization request is unique in that the <body/> element MUST
     * NOT possess a 'sid' attribute.
     */
    @Test
    public void sessionIDs() {
        testedBy(RequestValidator.class, "validateSessionCreationSID");
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 7.2: Session Creation Response

    /*
     * After receiving a new session request, the connection manager MUST
     * generate an opaque, unpredictable session identifier (or SID).
     */
    // BOSH CM functionality not supported.

    /*
     * The SID MUST be unique within the context of the connection manager
     * application.
     */
    // BOSH CM functionality not supported.

    /*
     * The <body/> element of the connection manager's response to the client's
     * session creation request MUST possess the following attributes (they
     * SHOULD NOT be included in any other responses): "sid", "wait".
     */
    // BOSH CM functionality not supported.

    /*
     * The "wait" time (in seconds) MUST be less than or equal to the value
     * specified in the session request.
     */
    // BOSH CM functionality not supported.

    /*
     * The <body/> element SHOULD also include the following attributes (they
     * SHOULD NOT be included in any other responses): "ver", "polling",
     * "inactivity", "requests", "hold".
     */
    // BOSH CM functionality not supported.

    /*
     * The "ver" attribute should be the highest version of the BOSH protocol
     * that the connection manager supports, or the version specified by the
     * client in its request, whichever is lower.
     */
    // BOSH CM functionality not supported.

    /*
     * The The RECOMMENDED values of the "requests" attribute are either "2"
     * or one more than the value of the 'hold' attribute specified in the
     * session request.
     */
    // BOSH CM functionality not supported.

    /*
     * The "hold" value MUST NOT be greater than the value specified by the
     * client in the session request.
     */
    // BOSH CM functionality not supported.

    /*
     * The connection manager MAY include an 'accept' attribute in the session
     * creation response element, to specify a space-separated list of the
     * content encodings it can decompress.
     */
    // BOSH CM functionality not supported.

    /*
     * After receiving a session creation response with an 'accept' attribute,
     * clients MAY include an HTTP Content-Encoding header in subsequent
     * requests (indicating one of the encodings specified in the 'accept'
     * attribute) and compress the bodies of the requests accordingly.
     */

    @Test(timeout=5000)
    public void requestEncodeGZIP() throws Exception {
        logTestStart();
        testedBy(ConnectionValidator.class, "assertRequestContentEncoding");
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
                .setAttribute(Attributes.ACCEPT, "gzip")
                .build();
        conn.sendResponse(scr);

        // Now attempt to send something
        session.send(ComposableBody.builder()
                .setNamespaceDefinition("foo", "http://bar/")
                .setPayloadXML("<foo:msg>hello world</foo:msg>")
                .build());
        conn = cm.awaitConnection();
        StubRequest req = conn.getRequest();
        assertEquals("gzip", req.getHeader("Content-Encoding"));
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, scr.getAttribute(Attributes.SID))
                .build());

        assertValidators(scr);
    }

    @Test(timeout=5000)
    public void requestEncodeZLIB() throws Exception {
        logTestStart();
        testedBy(ConnectionValidator.class, "assertRequestContentEncoding");
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
                .setAttribute(Attributes.ACCEPT, "deflate")
                .build();
        conn.sendResponse(scr);

        // Now attempt to send something
        session.send(ComposableBody.builder()
                .setNamespaceDefinition("foo", "http://bar/")
                .setPayloadXML("<foo:msg>hello world</foo:msg>")
                .build());
        conn = cm.awaitConnection();
        StubRequest req = conn.getRequest();
        assertEquals("deflate", req.getHeader("Content-Encoding"));
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, scr.getAttribute(Attributes.SID))
                .build());

        assertValidators(scr);
    }

    @Test(timeout=5000)
    public void requestEncodingWhenNotSupported() throws Exception {
        logTestStart();
        StubConnection conn;
        testedBy(ConnectionValidator.class, "assertRequestContentEncoding");

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
        conn.sendResponse(scr);

        // Now attempt to send something
        session.send(ComposableBody.builder()
                .setNamespaceDefinition("foo", "http://bar/")
                .setPayloadXML("<foo:msg>hello world</foo:msg>")
                .build());
        conn = cm.awaitConnection();
        StubRequest req = conn.getRequest();
        assertNull("Illegal encoding", req.getHeader("Content-Encoding"));
        conn.sendResponse(ComposableBody.builder()
                .setAttribute(Attributes.SID, scr.getAttribute(Attributes.SID))
                .build());

        assertValidators(scr);
    }

    /*
     * A connection manager MAY include an 'ack' attribute (set to the value
     * of the 'rid' attribute of the session creation request) to indicate that
     * it will be using acknowledgements throughout the session and that the
     * absence of an 'ack' attribute in any response is meaningful (see
     * Acknowledgements).
     */
    // BOSH CM functionality not supported
    // Covered by requirements in Section 9.

    /*
     * If the connection manager supports session pausing (see Inactivity) then
     * it SHOULD advertise that to the client by including a 'maxpause'
     * attribute in the session creation response element.
     */
    // BOSH CM functionality not supported
    // TODO: Client support and use of 'maxpause' attribute

    /*
     * For both requests and responses, the <body/> element and its content
     * SHOULD be UTF-8 encoded.
     */
    // BOSH CM functionality not supported.  Client portion is tested elsewhere.

    /*
     * If the HTTP Content-Type header of a request/response specifies a
     * character encoding other than UTF-8, then the connection manager MAY
     * convert between UTF-8 and the other character encoding.
     */
    // BOSH CM functionality not supported.
    // Not implemented in client.

    /*
     * The connection manager MAY inform the client which encodings it can
     * convert by setting the optional 'charsets' attribute in the session
     * creation response element to a space-separated list of encodings. [19]
     */
    // BOSH CM functionality not supported.
    // Not implemented in client.

    /*
     * As soon as the connection manager has established a connection to the
     * server and discovered its identity, it MAY forward the identity to the
     * client by including a 'from' attribute in a response, either in its
     * session creation response, or (if it has not received the identity from
     * the server by that time) in any subsequent response to the client.
     */
    // BOSH CM functionality not supported.
    // TODO: Client passthrough of CM 'from' attribute in response

    /*
     * If it established a secure connection to the server (as defined in
     * Initiating a BOSH Session), then in the same response the connection
     * manager SHOULD also include the 'secure' attribute set to "true" or "1".
     */
    // BOSH CM functionality not supported.
    // TODO: Client pass through of 'secure' attribute

}
