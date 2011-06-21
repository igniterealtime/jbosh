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

import java.util.logging.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * BOSH body/wrapper element tests.
 */
public class DynamicBodyTest extends BodyTest {

    private final Logger LOG = Logger.getLogger(DynamicBodyTest.class.getName());

    public AbstractBody createBody() {
        return ComposableBody.builder().build();
    }


    @Test
    public void testMutable() throws BOSHException {
        ComposableBody body = ComposableBody.builder().setPayloadXML("content").build();
        body = body.rebuild().setAttribute(Attributes.RID, "1").build();
        assertEquals("1", body.getAttribute(Attributes.RID));
        body.rebuild().setAttribute(Attributes.RID, "1").build();
        assertEquals("1", body.getAttribute(Attributes.RID));
    }

    @Test
    public void testComputedXML() throws BOSHException {
        ComposableBody body = ComposableBody.builder().setPayloadXML("content").build();
        assertEquals("<body xmlns='http://jabber.org/protocol/httpbind'>"
                + "content</body>", body.toXML());
    }

    @Test
    public void testComputedAttributeXML() throws BOSHException {
        ComposableBody body = ComposableBody.builder()
                .setPayloadXML("content")
                .setAttribute(Attributes.TO, "foo")
                .build();
        assertEquals("<body to='foo' "
                + "xmlns='http://jabber.org/protocol/httpbind'>"
                + "content</body>", body.toXML());
    }

    @Test
    public void testComputedNamespaceXML() throws BOSHException {
        ComposableBody body = ComposableBody.builder()
                .setPayloadXML("content")
                .setNamespaceDefinition("foo", "bar")
                .build();
        assertEquals("<body xmlns:foo='bar' "
                + "xmlns='http://jabber.org/protocol/httpbind'>"
                + "content</body>", body.toXML());
    }

    @Test
    public void testEscapedAttributeValue() throws BOSHException {
        ComposableBody body = ComposableBody.builder()
                .setPayloadXML("content")
                .setAttribute(Attributes.TO, "the ' catastrophe")
                .build();
        assertEquals("<body to='the &apos; catastrophe' "
                + "xmlns='http://jabber.org/protocol/httpbind'>"
                + "content</body>", body.toXML());
    }

    @Test
    public void testFromStatic() {
        final String uri = "http://jabber.org/protocol/httpbind";
        final String xmlns = "xmlns='" + uri + "'";
        final String boshNs = "xmlns:bosh='" + uri + "'";
        StaticBody sb;
        ComposableBody db;

        String statics[] = new String[] {
            "<body " + xmlns + "/>",
            "<body\t" + xmlns + "/>",
            "<body\r" + xmlns + "/>",
            "<body\n" + xmlns + "/>",
            "<body " + xmlns + "></body>",
            "<body " + xmlns + ">content</body>",
            "<body " + xmlns + ">con<body>te</body>nt</body>",
            "<bosh:body " + boshNs + "/>",
            "<bosh:body " + boshNs + ">content</bosh:body>",
            "<bosh:body " + boshNs + ">con<body>te</body>nt</bosh:body>",
            "<?xml version='1.0'?>\n<!-- --> <body " + xmlns + "/>",
            "<body " + xmlns + "/> <!-- foo> "
        };
        String payloads[] = new String[] {
            "",
            "",
            "",
            "",
            "",
            "content",
            "con<body>te</body>nt",
            "",
            "content",
            "con<body>te</body>nt",
            "",
            ""
        };
        assertEquals("Bad test definition", statics.length, payloads.length);
        
        for (int i=0; i<statics.length; i++) {
            String id = "statics[" + i + "]='" + statics[i] + "', payloads["
                    + i + "]='" + payloads[i] + "'";
            try {
                sb = StaticBody.fromString(statics[i]);
                db = ComposableBody.fromStaticBody(sb);
                assertEquals(id, payloads[i], db.getPayloadXML());
            } catch (BOSHException boshx) {
                boshx.printStackTrace(System.err);
                fail("Exception thrown during test " + id
                        + "  ==> " + boshx.getMessage());
            }
        }

    }

}
