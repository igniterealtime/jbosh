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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * BOSHClient tests.
 */
public class BOSHClientTest extends AbstractBOSHTest {

    @Test(timeout=5000)
    public void explicitConnectionClose() throws Exception {
        logTestStart();

        final List<BOSHClientConnEvent> events =
                new ArrayList<BOSHClientConnEvent>();
        session.addBOSHClientConnListener(new BOSHClientConnListener() {
            public void connectionEvent(BOSHClientConnEvent connEvent) {
                events.add(connEvent);
            }
        });

        // Session creation
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        session.drain();
        assertEquals(1, events.size());
        BOSHClientConnEvent event = events.remove(0);
        assertTrue(event.isConnected());
        assertFalse(event.isError());

        // Explicit session termination
        session.disconnect();
        conn = cm.awaitConnection();
        scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.TYPE, "terminate")
                .build();
        conn.sendResponse(scr);
        session.drain();
        assertEquals(1, events.size());
        event = events.remove(0);
        assertFalse(event.isConnected());
        assertFalse(event.isError());
    }

    @Test(timeout=5000)
    public void connectionCloseOnError() throws Exception {
        logTestStart();

        final List<BOSHClientConnEvent> events =
                new ArrayList<BOSHClientConnEvent>();
        session.addBOSHClientConnListener(new BOSHClientConnListener() {
            public void connectionEvent(BOSHClientConnEvent connEvent) {
                events.add(connEvent);
            }
        });

        // Session creation
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.WAIT, "1")
                .build();
        conn.sendResponse(scr);
        session.drain();
        assertEquals(1, events.size());
        BOSHClientConnEvent event = events.remove(0);
        assertTrue(event.isConnected());
        assertFalse(event.isError());

        // Session termination on error
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.TYPE, "terminate")
                .setAttribute(Attributes.CONDITION, "item-not-found")
                .build();
        conn.sendResponse(scr);
        session.drain();
        assertEquals(1, events.size());
        event = events.remove(0);
        assertFalse(event.isConnected());
        assertTrue(event.isError());
        Throwable cause = event.getCause();
        assertNotNull(cause);
        assertTrue(cause instanceof BOSHException);
        BOSHException boshEx = (BOSHException) cause;
        String msg = boshEx.getMessage();
        assertTrue(msg.contains(
                TerminalBindingCondition.ITEM_NOT_FOUND.getMessage()));
    }

}
