/*
 * Copyright 2009 Guenther Niess
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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;

/**
 * Implementation of the {@code HTTPSender} interface which uses the
 * Apache HttpClient API to send messages to the connection manager.
 */
final class ApacheHTTPSender implements HTTPSender {

    /**
     * Lock used for internal synchronization.
     */
    private final Lock lock = new ReentrantLock();

    /**
     * Session configuration.
     */
    private BOSHClientConfig cfg;

    /**
     * HttpClient instance to use to communicate.
     */
    private HttpClient httpClient;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors:

    /**
     * Prevent construction apart from our package.
     */
    ApacheHTTPSender() {
        // Load Apache HTTP client class
        HttpClient.class.getName();
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
            httpClient = initHttpClient(session);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    public void destroy() {
        lock.lock();
        try {
            if (httpClient != null) {
                httpClient.getConnectionManager().shutdown();
            }
        } finally {
            cfg = null;
            httpClient = null;
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public HTTPResponse send(
            final CMSessionParams params,
            final AbstractBody body) {
        HttpClient mClient;
        BOSHClientConfig mCfg;
        lock.lock();
        try {
            if (httpClient == null) {
                httpClient = initHttpClient(cfg);
            }
            mClient = httpClient;
            mCfg = cfg;
        } finally {
            lock.unlock();
        }
        return new ApacheHTTPResponse(mClient, mCfg, params, body);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Package-private methods:

    @SuppressWarnings("deprecation")
    private static synchronized HttpClient initHttpClient(final BOSHClientConfig config) {
        // Create and initialize HTTP parameters
        org.apache.http.params.HttpParams params = new org.apache.http.params.BasicHttpParams();
        org.apache.http.conn.params.ConnManagerParams.setMaxTotalConnections(params, 100);
        org.apache.http.params.HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        org.apache.http.params.HttpProtocolParams.setUseExpectContinue(params, false);
        if (config != null &&
                config.getProxyHost() != null &&
                config.getProxyPort() != 0) {
            HttpHost proxy = new HttpHost(
                    config.getProxyHost(),
                    config.getProxyPort());
            params.setParameter(org.apache.http.conn.params.ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        // Create and initialize scheme registry 
        org.apache.http.conn.scheme.SchemeRegistry schemeRegistry = new org.apache.http.conn.scheme.SchemeRegistry();
        schemeRegistry.register(
                new org.apache.http.conn.scheme.Scheme("http", org.apache.http.conn.scheme.PlainSocketFactory.getSocketFactory(), 80));

            org.apache.http.conn.ssl.SSLSocketFactory sslFactory;
            if(null == config.getSSLContext()) {
                sslFactory = org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory();
                sslFactory.setHostnameVerifier(org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            } else {
                sslFactory = new org.apache.http.conn.ssl.SSLSocketFactory(config.getSSLContext());
            }
            schemeRegistry.register(
                    new org.apache.http.conn.scheme.Scheme("https", sslFactory, 443));

        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        org.apache.http.conn.ClientConnectionManager cm = new org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager(params, schemeRegistry);
        return new org.apache.http.impl.client.DefaultHttpClient(cm, params);
    }
}
