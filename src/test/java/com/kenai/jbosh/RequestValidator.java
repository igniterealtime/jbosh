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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import static org.junit.Assert.*;

/**
 * BOSHClientRequestListener which validates the form and content of the
 * all requests made to the CM by the BOSHClient session.
 */
public class RequestValidator
        implements BOSHClientRequestListener, BOSHClientResponseListener {

    private static final Logger LOG =
            Logger.getLogger(RequestValidator.class.getName());

    private final static String SCHEMA_RESOURCE = "schema.xsd";
    
    private static final Validator VALIDATOR;

    private final AtomicReference<AbstractBody> sessionCreationResponse =
            new AtomicReference<AbstractBody>();

    private final ConcurrentLinkedQueue<AbstractBody> outstandingRequests =
            new ConcurrentLinkedQueue<AbstractBody>();
    
    private final AtomicReference<AbstractBody> lastRequestResponded =
            new AtomicReference<AbstractBody>();

    private List<Node> requests = null;

    static {
        try {
            ClassLoader loader = RequestValidator.class.getClassLoader();
            URL url = loader.getResource(SCHEMA_RESOURCE);
            assertNotNull(url);
            SchemaFactory factory = SchemaFactory.newInstance(
                    XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(url);
            VALIDATOR = schema.newValidator();
        } catch (SAXException saxx) {
            // If the problem is resolution of xml:lang, check your internet
            // connection.
            throw(new IllegalStateException(
                    "Could not intialize schema validator: " + saxx));
        }
    }

    /**
     * Class used to associate timing info with sent messages.
     */
    private static class Node {
        private final long timestamp;
        private final AbstractBody body;
        private final AbstractBody respTo;

        /**
         * Constructor.
         *
         * @param stamp time in millis when the msg was sent
         * @param msg the sent msg
         * @param reqRespTo the last request responded to
         */
        public Node(
                final long stamp,
                final AbstractBody msg,
                final AbstractBody reqRespTo) {
            timestamp = stamp;
            body = msg;
            respTo = reqRespTo;
        }

        /**
         * Get the time the msg was sent.
         *
         * @return time in millis
         */
        public long getTime() {
            return timestamp;
        }

        /**
         * Get the msg that was sent.
         *
         * @return msg
         */
        public AbstractBody getBody() {
            return body;
        }

        /**
         * Get the last request which has been responded to.
         *
         * @return last request responded to
         */
        public AbstractBody getLastRequestRespondedTo() {
            return respTo;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void requestSent(final BOSHMessageEvent event) {
        if (requests == null) {
            requests = new CopyOnWriteArrayList<Node>();
        }
        outstandingRequests.add(event.getBody());
        Node node = new Node(
                System.currentTimeMillis(),
                event.getBody(),
                lastRequestResponded.get());
        requests.add(node);
    }

    /**
     * {@inheritDoc}
     */
    public void responseReceived(final BOSHMessageEvent event) {
        lastRequestResponded.set(outstandingRequests.remove());
    }

    /**
     * Reset the request counter.  The nest request received will be expected
     * to conform to the first request semantics.
     */
    public void reset() {
        requests = null;
    }

    /**
     * Set the session creation response to use when validating the
     * communications.  If set, additional validations will be applied.
     *
     * @param response session creation response message
     */
    public void setSessionCreationResponse(final AbstractBody response) {
        sessionCreationResponse.set(response);
    }

    /**
     * Checks that the validator didn't encounter any problems.  If it did,
     * it should perform the assertions here.
     *
     * @param scr session creation response message to use in validation,
     *  or {@code null} to skip those checks
     */
    public void checkAssertions(final AbstractBody scr) {
        if (requests == null) {
            // Nothing to validate.
            LOG.fine("Nothing to validate");
            return;
        }
        sessionCreationResponse.set(scr);
        LOG.fine("Validating " + requests.size() + " message(s)"); 
        Node previous = null;
        Node first = null;
        int index = 0;
        for (Node node : requests) {
            if (first == null) {
                first = node;
            }

            String rid = node.getBody().getAttribute(Attributes.RID);
            LOG.fine("Validating msg #" + index + " (RID: " + rid + ")");
            validateRequest(index, first, previous, node);
            previous = node;
            index++;
        }
    }

    /**
     * Validate a request.
     *
     * @param message number (zero-based)
     * @param first message sent
     * @param previous previous request, or {@code null} if this is the
     *  first request ever sent
     * @param request request message
     */
    private void validateRequest(
            final int idx,
            final Node first,
            final Node previous,
            final Node request) {
        try {
            assertValidXML(request);
            assertSingleBodyElement(request);
            assertNoComments(request);
            assertNoProcessingInstructions(request);
            assertRequestIDSequential(request, previous);
            if (previous == null) {
                validateRequestHeaders(idx, request, previous);
                assertSessionCreationRequestID(request);
                assertSessionCreationRequestIDRange(request);
                validateSessionCreationAck(idx, request);
                validateSessionCreationSID(idx, request);
                validateSessionCreationHold(idx, request);
            } else {
                validateRequestHeaders(idx, request, previous);
                validateSubsequentRequestSID(idx, request);
                validateSubsequestRequestAck(idx, first, request);
                validateSubsequentPause(idx, request, previous);
            }
        } catch (AssertionError err) {
            LOG.info("Assertion failed for request #" + idx + ": "
                    + err.getMessage() + "\n" + request.getBody().toXML());
            throw(err);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 6: <body/> Wrapper Element

    /**
     * The body of each HTTP request and response contains a single <body/>
     * wrapper element qualified by the 'http://jabber.org/protocol/httpbind'
     * namespace.
     *
     * @param body message to validate
     */
    private void assertSingleBodyElement(final Node node) {
        NodeList nSet;
        nSet = (NodeList) bodyXPath(node, "/bosh:body", XPathConstants.NODESET);
        assertEquals("BOSH body count", 1, nSet.getLength());
        nSet = (NodeList) bodyXPath(node, "/*", XPathConstants.NODESET);
        assertEquals("Body count", 1, nSet.getLength());
    }

    /**
     *  XEP-0124 Section 6:
     *
     * The <body/> element and its content together MUST conform to the
     * specifications set out in XML 1.0 [14]
     *
     * and...
     *
     * They SHOULD also conform to Namespaces in XML [15].
     *
     * and...
     *
     * The content MUST NOT contain Partial XML elements.
     *
     * @param request request to validate
     */
    private void assertValidXML(final Node request) {
        ByteArrayInputStream stream = new ByteArrayInputStream(
                request.getBody().toXML().getBytes());
        StreamSource source = new StreamSource(stream);
        Throwable thr;
        try {
            synchronized(VALIDATOR) {
                VALIDATOR.validate(source);
            }
            return;
        } catch (IOException iox) {
            thr = iox;
        } catch (SAXException saxx) {
            thr = saxx;
        }
        fail("Request XML validation failed: " + thr.getMessage());
    }

    /**
     * The content MUST NOT contain XML comments.
     *
     * @param node message to validate
     */
    private void assertNoComments(final Node node) {
        NodeList nSet;
        nSet = (NodeList) bodyXPath(
                node, "//comment()", XPathConstants.NODESET);
        assertEquals("XML comment count", 0, nSet.getLength());
    }

    /**
     * The content MUST NOT contain XML processing instructions.
     *
     * @param node message to validate
     */
    private void assertNoProcessingInstructions(final Node node) {
        NodeList nSet;
        nSet = (NodeList) bodyXPath(
                node, "//processing-instruction()",
                XPathConstants.NODESET);
        assertEquals("Processing instruction count", 0, nSet.getLength());
    }

    /**
     * The <body/> element of every client request MUST possess a sequential
     * request ID encapsulated via the 'rid' attribute.
     *
     * @param node node to validate
     * @param previous previous node
     */
    private void assertRequestIDSequential(
            final Node node, final Node previous) {
        String ridStr = node.getBody().getAttribute(Attributes.RID);
        assertNotNull("Request ID attribute not present", ridStr);

        long rid = Long.parseLong(ridStr);
        if (previous != null) {
            String prevRidStr = previous.getBody().getAttribute(Attributes.RID);
            assertNotNull("Previous request ID attribute not present",
                    prevRidStr);
            long prevRid = Long.parseLong(prevRidStr);
            assertEquals("Request ID is not sequential", prevRid + 1, rid);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 7.1: Session Creation Request

    /**
     * The <body/> element of the first request SHOULD possess the following
     * attributes (they SHOULD NOT be included in any other requests except as
     * specified under Adding Streams To A Session): "to", "xml:lang", "ver",
     * "wait", "hold".
     *
     * @param message number (zero-based)
     * @param request request message
     * @param previous previous request message
     */
    private void validateRequestHeaders(
            final int idx,
            final Node request,
            final Node previous) {
        AbstractBody body = request.getBody();
        if (previous == null) {
            // session creation request
            assertNotNull("to attribute not present in request #" + idx,
                    body.getAttribute(Attributes.TO));
            assertNotNull("xml:lang attribute not present in request #" + idx,
                    body.getAttribute(Attributes.XML_LANG));
            assertNotNull("ver attribute not present in request #" + idx,
                    body.getAttribute(Attributes.VER));
            assertNotNull("wait attribute not present in request #" + idx,
                    body.getAttribute(Attributes.WAIT));
            assertNotNull("hold attribute not present in request #" + idx,
                    body.getAttribute(Attributes.HOLD));
        } else {
            // subsequent request
            assertNull("to attribute was present in request #" + idx,
                    body.getAttribute(Attributes.TO));
            assertNull("xml:lang attribute was present in request #" + idx,
                    body.getAttribute(Attributes.XML_LANG));
            assertNull("ver attribute was present in request #" + idx,
                    body.getAttribute(Attributes.VER));
            assertNull("wait attribute was present in request #" + idx,
                    body.getAttribute(Attributes.WAIT));
            assertNull("hold attribute was present in request #" + idx,
                    body.getAttribute(Attributes.HOLD));
        }
    }

    /**
     * The initialization request is unique in that the <body/> element MUST
     * NOT possess a 'sid' attribute.
     *
     * @param message number (zero-based)
     * @param request request message
     */
    private void validateSessionCreationSID(
            final int idx,
            final Node request) {
        assertNull("sid attribute was present in request #" + idx,
                request.getBody().getAttribute(Attributes.SID));
    }

    /**
     * A client MAY include an 'ack' attribute (set to "1") to indicate
     * that it will be using acknowledgements throughout the session
     * If the client will be including 'ack' attributes on requests during a
     * session, then it MUST include an 'ack' attribute (set to '1') in its
     * session creation request, and set the 'ack' attribute of requests
     * throughout the session.
     *
     * @param message number (zero-based)
     * @param request request message
     */
    private void validateSessionCreationAck(
            final int idx,
            final Node request) {
        String ack = request.getBody().getAttribute(Attributes.ACK);
        if (ack != null) {
            assertEquals("intial ack must be 1", "1", ack);
        }
    }

    /**
     * If the client is not able to use HTTP Pipelining then the "hold"
     * attribute SHOULD be set to "1".
     *
     * @param message number (zero-based)
     * @param request request message
     */
    private void validateSessionCreationHold(
            final int idx,
            final Node request) {
        String hold = request.getBody().getAttribute(Attributes.HOLD);
        assertNotNull("hold attribute was not present in initial request",
                hold);
        assertEquals("incorrect hold attrivute value", "1", hold);
    }


    /**
     * All requests after the first one MUST include a valid 'sid' attribute.
     *
     * @param message number (zero-based)
     * @param request request message
     * @param previous previous request, or {@code null} if this is the
     *  first request ever sent
     */
    private void validateSubsequentRequestSID(
            final int idx,
            final Node request) {
        assertNotNull("sid attribute not present in request #" + idx,
                request.getBody().getAttribute(Attributes.SID));
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 7.2: Session Creation Response

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 9.2: Response Acknowledgements

    /**
     * If the client will be including 'ack' attributes on requests during a
     * session, then it MUST include an 'ack' attribute (set to '1') in its
     * session creation request, and set the 'ack' attribute of requests
     * throughout the session.
     *
     * @param idx message number (zero-based)
     * @param first first message sent
     * @param request message to validate
     */
    private void validateSubsequestRequestAck(
            final int idx,
            final Node first,
            final Node request) {
        if (request == first) {
            // Nothing to check on first request
            return;
        }
        String ack = first.getBody().getAttribute(Attributes.ACK);
        if (ack == null) {
            String subAck = request.getBody().getAttribute(Attributes.ACK);
            assertNull("subsequent request #" + idx + " can only use acks if "
                    + "advertized in session creation request", subAck);
        }

        AbstractBody resp = request.getLastRequestRespondedTo();
        Long lastRID = Long.parseLong(resp.getAttribute(Attributes.RID));
        ack = request.getBody().getAttribute(Attributes.ACK);
        if (ack == null) {
            // Verify implicit ack
            String rid = request.getBody().getAttribute(Attributes.RID);
            long implicit = lastRID.longValue() + 1L;
            assertEquals("Implicit ack when RID != prev + 1",
                    Long.valueOf(implicit).toString(), rid);
        } else {
            assertEquals("Request ack incorrect", lastRID.toString(), ack);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 10: Inactivity

    /**
     * If the connection manager did not specify a 'maxpause' attribute at the
     * start of the session then the client MUST NOT send a 'pause' attribute
     * during the session.
     *
     * @param idx message number (zero-based)
     * @param request message to validate
     */
    private void validateSubsequentPause(
            final int idx,
            final Node request,
            final Node previous) {
        AbstractBody scr = sessionCreationResponse.get();
        if (scr == null) {
            // Not checking this
            return;
        }

        try {
            // Check the current node:
            AttrPause pause = AttrPause.createFromString(
                    request.getBody().getAttribute(Attributes.PAUSE));
            if (pause != null) {
                AttrMaxPause maxPause = AttrMaxPause.createFromString(
                        scr.getAttribute(Attributes.MAXPAUSE));
                assertNotNull("Request #" + idx + " can only use pause when "
                        + "advertized in session creation response", maxPause);
                assertTrue(pause.intValue() < maxPause.intValue());
            }

            // Check the previous node
            AttrPause prevPause = AttrPause.createFromString(
                    previous.getBody().getAttribute(Attributes.PAUSE));
            if (prevPause != null) {
                long delta = request.getTime() - previous.getTime();
                if (delta > prevPause.getInMilliseconds()) {
                    fail("Request #" + idx + " was sent too late relative to "
                        + "the previous pause message (delta=" + delta
                        + ", pause=" + prevPause.getInMilliseconds() + ")");
                }
            }
        } catch (BOSHException boshx) {
            fail("Could not parse pause/maxpause: " + boshx.getMessage());
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // XEP-0124 Section 14.1: Generation

    /*
     * The client MUST generate a large, random, positive integer for the
     * initial 'rid' (see Security Considerations) and then increment that
     * value by one for each subsequent request.
     *
     * @param node node to validate
     */
    private void assertSessionCreationRequestID(
            final Node node) {
        String ridStr = node.getBody().getAttribute(Attributes.RID);
        assertNotNull("Request ID attribute not present", ridStr);

        long rid = Long.parseLong(ridStr);
        assertTrue("RID was <= 0", rid > 0);

        // Not checking to see if it is "large" since it is already random
    }

    /*
     * The client MUST take care to choose an initial 'rid' that will never be
     * incremented above 9007199254740991 [21] within the session.
     *
     * @param node node to validate
     */
    private void assertSessionCreationRequestIDRange(
            final Node node) {
        String ridStr = node.getBody().getAttribute(Attributes.RID);
        long rid = Long.parseLong(ridStr);
        BigInteger biRID = BigInteger.valueOf(rid);
        BigInteger biMax = new BigInteger("9007199254740991");
        BigInteger biThreshold = BigInteger.valueOf(
                Math.round(Math.pow(2.0, 20.0)));
        assertTrue(
                "Initial RID leaves fewer than "
                + biThreshold.toString()
                + " total requests before max RID limit is hit",
                biRID.compareTo(biMax.subtract(biThreshold)) <= 0);
    }

    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Execute an XPath against a AbstractBody instance.
     *
     * @param body node with body to XPath against
     * @param xpath the XPath expression
     * @param returnType type to return
     * @return resulting node
     */
    private Object bodyXPath(
            final Node node,
            final String xpath,
            final QName returnType) {
        try {
            String xml = node.getBody().toXML();
            ByteArrayInputStream stream =
                    new ByteArrayInputStream(xml.getBytes());
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(stream);
            XPathFactory xpf = XPathFactory.newInstance();
            XPath xp = xpf.newXPath();
            xp.setNamespaceContext(new NamespaceContext() {
                Map<String, String> map = new HashMap<String, String>();
                {
                    map.put("bosh", BodyQName.BOSH_NS_URI);
                    map.put("xml", XMLConstants.XML_NS_URI);
                }

                public String getNamespaceURI(String prefix) {
                    return map.get(prefix);
                }

                public String getPrefix(String namespaceURI) {
                    throw new UnsupportedOperationException("Not supported.");
                }

                public Iterator getPrefixes(String namespaceURI) {
                    throw new UnsupportedOperationException("Not supported.");
                }

            });
            return xp.evaluate(xpath, document, returnType);
        } catch (Throwable thr) {
            fail("Could not parse body to DOM document: " + thr.getMessage());
        }
        return null;
    }


}
