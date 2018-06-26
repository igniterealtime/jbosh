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

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;

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

    private static synchronized HttpClient initHttpClient(final BOSHClientConfig config) {

        SSLConnectionSocketFactory sslFactory =
                new SSLConnectionSocketFactory(
                        getSslContext(config));

        ConnectionSocketFactory csf = PlainConnectionSocketFactory.getSocketFactory();

        Registry<ConnectionSocketFactory> r = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", csf)
                .register("https", sslFactory)
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(r);
        cm.setMaxTotal(100);
        RequestConfig rc = RequestConfig.custom()
                .setExpectContinueEnabled(false)
                .setContentCompressionEnabled(config.isCompressionEnabled())
                .build();
        HttpClientBuilder httpClientBuilder = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(rc);

        if (config.getProxyHost() != null &&
                config.getProxyPort() != 0) {
            HttpHost proxy = new HttpHost(
                    config.getProxyHost(),
                    config.getProxyPort());
            httpClientBuilder.setProxy(proxy);
        }

        return httpClientBuilder.build();
    }

    private static SSLContext getSslContext(BOSHClientConfig config) {
        if (config.getSSLContext() != null) {
            return config.getSSLContext();
        }
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
