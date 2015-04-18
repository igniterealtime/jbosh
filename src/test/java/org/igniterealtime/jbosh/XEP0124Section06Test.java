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

import static org.junit.Assert.fail;

import java.util.logging.Logger;

import org.igniterealtime.jbosh.AbstractBody;
import org.igniterealtime.jbosh.Attributes;
import org.igniterealtime.jbosh.ComposableBody;
import org.igniterealtime.jbosh.StaticBody;
import org.junit.Test;

/**
 * BOSH XEP-0124 specification section 6 tests:  &lt;body/&gt; Wrapper Element.
 */
public class XEP0124Section06Test extends AbstractBOSHTest {

    private static final Logger LOG =
            Logger.getLogger(XEP0124Section06Test.class.getName());

    /*
     * The body of each HTTP request and response contains a single <body/>
     * wrapper element qualified by the 'http://jabber.org/protocol/httpbind'
     * namespace.
     */
    @Test(timeout=5000)
    public void singleBodyElementPerRequest() throws Exception {
        logTestStart();
        testedBy(RequestValidator.class, "assertSingleBodyElement");
        String body = "<body xmlns='" + NS_URI + "'></body>";
        StubConnection conn;
        StaticBody sb;

        // Attempt to send a body containing multiple body elements
        sb = StaticBody.fromString(body + body + body);
        ComposableBody db = ComposableBody.fromStaticBody(sb);
        LOG.info("Sending: " + db.toXML());
        session.send(ComposableBody.fromStaticBody(sb));
        conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        boolean failed = false;
        try {
            assertValidators(scr);
            failed = true;
        } catch (AssertionError err) {
            failed = false;
        }
        if (failed) {
            fail("Did not catch multiple bodies");
        }
    }

    /*
     * The <body/> element and its content together MUST conform to the
     * specifications set out in XML 1.0 [14]
     */
    @Test
    public void conformToXML1() {
        testedBy(RequestValidator.class, "assertValidXML");
    }

    /*
     * They SHOULD also conform to Namespaces in XML [15].
     */
    @Test
    public void validXML() {
        testedBy(RequestValidator.class, "assertValidXML");
    }

    /*
     * The content MUST NOT contain Partial XML elements.
     */
    @Test(timeout=5000)
    public void noPartialElements() throws Exception {
        logTestStart();
        testedBy(RequestValidator.class, "assertValidXML");
        String body1 = "<body xmlns='" + NS_URI
                + "'><foo xmlns='http://foo.com/'>"
                + "<partiallElement></foo></body>";

        // Attempt to send a body containing comments
        StaticBody sb = StaticBody.fromString(body1);
        session.send(ComposableBody.fromStaticBody(sb));
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        boolean failed = false;
        try {
            assertValidators(scr);
            failed = true;
        } catch (AssertionError err) {
            failed = false;
        }
        if (failed) {
            fail("Did not catch partial element");
        }
    }

    /*
     * The content MUST NOT contain XML comments.
     */
    @Test(timeout=5000)
    public void noXMLComments() throws Exception {
        logTestStart();
        testedBy(RequestValidator.class, "assertNoComments");
        String body1 = "<!-- One --><body xmlns='" + NS_URI
                + "'><!-- Aha! --></body>";

        // Attempt to send a body containing comments
        StaticBody sb = StaticBody.fromString(body1);
        session.send(ComposableBody.fromStaticBody(sb));
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        boolean failed = false;
        try {
            assertValidators(scr);
            failed = true;
        } catch (AssertionError err) {
            failed = false;
        }
        if (failed) {
            fail("Did not catch comments");
        }
    }

    /*
     * The content MUST NOT contain XML processing instructions.
     */
    @Test(timeout=5000)
    public void noProcessingInstructions() throws Exception {
        logTestStart();
        testedBy(RequestValidator.class, "assertNoProcessingInstructions");
        String body1 = "<body xmlns='" + NS_URI + "'><?foo bar='baz'?></body>";
        StubConnection conn;
        StaticBody sb;

        // Attempt to send a body containing processing instruction
        sb = StaticBody.fromString(body1);
        session.send(ComposableBody.fromStaticBody(sb));
        conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        boolean failed = false;
        try {
            assertValidators(scr);
            failed = true;
        } catch (AssertionError err) {
            failed = false;
        }
        if (failed) {
            fail("Did not catch processing instruction");
        }
    }

    /*
     * The content MUST NOT contain Internal or external DTD subsets.
     */
    // TODO: How do we validate internal/external DTD subsets?

    /*
     * The content MUST NOT contain Internal or external entity references
     * (with the exception of predefined entities).
     */
    // TODO: How do we validate internal/external entity references?

    /*
     * The <body/> wrapper MUST NOT contain any XML character data, although
     * its child elements MAY contain character data.
     */
    @Test(timeout=5000)
    public void validateNoBodyCharacterData() throws Exception{
        logTestStart();
        testedBy(RequestValidator.class, "assertValidXML");
        String body1 = "<body xmlns='" + NS_URI + "'>character data</body>";
        StubConnection conn;
        StaticBody sb;

        // Attempt to send a body containing processing instruction
        sb = StaticBody.fromString(body1);
        session.send(ComposableBody.fromStaticBody(sb));
        conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        boolean failed = false;
        try {
            assertValidators(scr);
            failed = true;
        } catch (AssertionError err) {
            failed = false;
        }
        if (failed) {
            fail("Did not catch character data");
        }
    }

    @Test(timeout=5000)
    public void validateChildElementCharacterData() throws Exception{
        logTestStart();
        StubConnection conn;

        // Attempt to send a body containing processing instruction
        session.send(ComposableBody.builder()
                .setNamespaceDefinition("foo", "http://foo.com/")
                .setPayloadXML("<foo:bar>text is present</foo:bar>")
                .build());
        conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        assertValidators(scr);
    }

    /*
     * The <body/> wrapper MUST contain zero or more complete XML immediate
     * child elements (called "payloads" in this document, e.g., XMPP stanzas
     * as defined in RFC 3920 or elements containing XML character data that
     * represents objects using the JSON data interchange format as defined in
     * RFC 4627 [16]).
     */
    @Test
    public void completeXMLChildElements() {
        testedBy(RequestValidator.class, "assertValidXML");
    }

    /*
     * Each <body/> wrapper MAY contain payloads qualified under a wide
     * variety of different namespaces.
     */
    @Test(timeout=5000)
    public void sendBodyWithVariousNamespaces() throws Exception {
        logTestStart();
        // Attempt to send a body containing various namespaces
        session.send(ComposableBody.builder()
                .setNamespaceDefinition("foo", "http://foo/")
                .setNamespaceDefinition("bar", "http://bar/")
                .setPayloadXML("<foo:bah/><bar:baz/>")
                .build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        assertValidators(scr);
    }

    /*
     * The <body/> element of every client request MUST possess a sequential
     * request ID encapsulated via the 'rid' attribute.
     */
    @Test(timeout=5000)
    public void sequentialRIDs() throws Exception {
        logTestStart();
        testedBy(RequestValidator.class, "assertRequestIDSequential");
        AbstractBody scr = null;
        for (int i=0; i<5; i++) {
            session.send(ComposableBody.builder().build());
            StubConnection conn = cm.awaitConnection();
            AbstractBody req = conn.getRequest().getBody();
            if (i == 0) {
                // Session creation response
                String waitStr = req.getAttribute(Attributes.WAIT);
                String verStr = req.getAttribute(Attributes.VER);
                String holdStr = req.getAttribute(Attributes.HOLD);
                scr = ComposableBody.builder()
                        .setAttribute(Attributes.SID, "1")
                        .setAttribute(Attributes.WAIT, waitStr)
                        .setAttribute(Attributes.VER, verStr)
                        .setAttribute(Attributes.INACTIVITY, "3")
                        .setAttribute(Attributes.HOLD, holdStr)
                        .build();
                conn.sendResponse(scr);
            } else {
                String sidStr = req.getAttribute(Attributes.SID);
                conn.sendResponse(ComposableBody.builder()
                        .setAttribute(Attributes.SID, sidStr)
                        .build());
            }
        }
        assertValidators(scr);
    }

}
