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

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xlightweb.client.HttpClient;

/**
 * Implementation of the {@code HTTPSender} interface which uses the
 * xLightweb API to send messages to the connection manager.
 */
final class XLightWebSender implements HTTPSender {

    /**
     * Logger.
     */
    private static final Logger LOG =
            Logger.getLogger(XLightWebSender.class.getName());

    /**
     * Lock used for internal synchronization.
     */
    private final Lock lock = new ReentrantLock();

    /**
     * HttpClient instance to use to communicate.
     */
    private HttpClient client;

    /**
     * Session configuration.
     */
    private BOSHClientConfig cfg;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors:

    /**
     * Prevent construction apart from our package.
     */
    XLightWebSender() {
        // Empty
    }

    ///////////////////////////////////////////////////////////////////////////
    // HTTPSender interface methods:

    /**
     * {@inheritDoc}
     */
    public void init(final BOSHClientConfig session) {
        lock.lock();
        try {
            cfg = session;
            client = new HttpClient();
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void destroy() {
        lock.lock();
        try {
            client.close();
        } catch (IOException iox) {
            LOG.log(Level.FINEST, "Ignoring exception on close", iox);
        } finally {
            client = null;
            cfg = null;
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public HTTPResponse send(final CMSessionParams params, final AbstractBody body) {
        HttpClient mClient;
        BOSHClientConfig mCfg;
        lock.lock();
        try {
            mClient = client;
            mCfg = cfg;
        } finally {
            lock.unlock();
        }
        return new XLightWebResponse(mClient, mCfg, params, body);
    }

}
