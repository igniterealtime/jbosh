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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xlightweb.BadMessageException;
import org.xlightweb.IHttpExchange;
import org.xlightweb.IHttpRequestHandler;
import org.xlightweb.server.HttpServer;

/**
 * Connection Manager stub used to act as a target server for functional
 * testing.  It sets up a servlet acting as a CM, exposing request processing
 * via an API which can be used by the tests to verify conditions and
 * setup preconfigured responses.
 */
public class StubCM {

    private static final Logger LOG =
            Logger.getLogger(StubCM.class.getName());
    private final HttpServer server;
    private final int port;
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final List<StubConnection> list = new ArrayList<StubConnection>();
    private final List<StubConnection> all = new ArrayList<StubConnection>();
    private final Set<StubCMListener> listeners =
            new CopyOnWriteArraySet<StubCMListener>();

    ///////////////////////////////////////////////////////////////////////////
    // Classes:

    private class ReqHandler implements IHttpRequestHandler {
        public void onRequest(IHttpExchange exchange)
                throws IOException, BadMessageException {
            try {
                StubConnection conn = new StubConnection(exchange);
                fireReceived(conn);
                lock.lock();
                try {
                    list.add(conn);
                    all.add(conn);
                    notEmpty.signalAll();
                } finally {
                    lock.unlock();
                }
                conn.awaitResponse();
                fireCompleted(conn);
                conn.executeResponse();
            } catch (Throwable thr) {
                LOG.log(Level.WARNING, "Uncaught throwable", thr);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Constructor:

    public StubCM() throws Exception {
        server = new HttpServer(probeForPort(), new ReqHandler());
        server.start();
        port = server.getLocalPort();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public methods:

    public void addStubCMListener(final StubCMListener listener) {
        listeners.add(listener);
    }

    public void removeStubCMListener(final StubCMListener listener) {
        listeners.remove(listener);
    }

    public URI getURI() {
        return URI.create("http://localhost:" + port + "/");
    }

    public void dispose() throws Exception {
        list.clear();
        all.clear();
        server.close();
    }

    public StubConnection awaitConnection() throws InterruptedException {
        lock.lock();
        try {
            while (list.isEmpty()) {
                notEmpty.await();
            }
            return list.remove(0);
        } finally {
            lock.unlock();
        }
    }

    public List<StubConnection> getConnections() {
        lock.lock();
        try {
            List<StubConnection> result = new ArrayList<StubConnection>();
            result.addAll(all);
            return result;
        } finally {
            lock.unlock();
        }
    }

    public int pendingConnectionCount() {
        lock.lock();
        try {
            return list.size();
        } finally {
            lock.unlock();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Private methods:

    private static int probeForPort() {
        int port = 17151;
        do {
            try {
                ServerSocket sock = new ServerSocket(port);
                sock.setReuseAddress(true);
                sock.close();
                return port;
            } catch (IOException iox) {
                // Ignore
            }
            port++;
        } while(true);
    }

    private void fireReceived(final StubConnection conn) {
        for (StubCMListener listener : listeners) {
            listener.requestReceived(conn);
        }
    }

    private void fireCompleted(final StubConnection conn) {
        for (StubCMListener listener : listeners) {
            listener.requestCompleted(conn);
        }
    }

}
