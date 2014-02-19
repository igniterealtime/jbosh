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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.igniterealtime.jbosh.AbstractBody;
import org.igniterealtime.jbosh.AttrAccept;
import org.igniterealtime.jbosh.BOSHException;
import org.igniterealtime.jbosh.GZIPCodec;
import org.igniterealtime.jbosh.ZLIBCodec;
import org.xlightweb.HttpResponse;
import org.xlightweb.HttpResponseHeader;
import org.xlightweb.IHttpExchange;

/**
 * Request/response pair as exposed from the stub connection manager
 * implementation.
 */
public class StubConnection {

    private static final Logger LOG =
            Logger.getLogger(StubConnection.class.getName());
    private final IHttpExchange exchange;
    private final AtomicReference<HttpResponse> httpResp =
            new AtomicReference<HttpResponse>();
    private final StubRequest req;
    private final AtomicReference<StubResponse> resp =
            new AtomicReference<StubResponse>();

    ///////////////////////////////////////////////////////////////////////////
    // Constructor:

    StubConnection(
            final IHttpExchange exch) {
        req = new StubRequest(exch.getRequest());
        exchange = exch;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public methods:

    public StubRequest getRequest() {
        return req;
    }

    public StubResponse getResponse() {
        return resp.get();
    }

    public void sendResponse(final AbstractBody respBody) throws IOException {
        sendResponseWithStatus(respBody, 200);
    }

    public void sendResponseWithStatus(
            final AbstractBody respBody,
            final int httpStatus)
            throws IOException {
        sendResponseWithStatusAndHeaders(respBody, httpStatus, null);
    }

    public void sendResponseWithStatusAndHeaders(
            final AbstractBody respBody,
            final int httpStatus,
            final Map<String,String> headers)
            throws IOException {
        HttpResponseHeader respHead = new HttpResponseHeader(httpStatus);
        if (headers != null) {
            for (Map.Entry<String,String> entry : headers.entrySet()) {
                respHead.setHeader(entry.getKey(), entry.getValue());
            }
        }
        
        String bodyStr = respBody.toXML();
        byte[] data = bodyStr.getBytes("UTF-8");
        try {
            String acceptStr = req.getHeader("Accept-Encoding");
            AttrAccept accept = AttrAccept.createFromString(acceptStr);
            if (accept != null) {
                String encoding = null;
                if (accept.isAccepted(ZLIBCodec.getID())) {
                    encoding = ZLIBCodec.getID();
                    data = ZLIBCodec.encode(data);
                } else if (accept.isAccepted(GZIPCodec.getID())) {
                    encoding = GZIPCodec.getID();
                    data = GZIPCodec.encode(data);
                }
                if (encoding != null) {
                    LOG.fine("Encoding: " + encoding);
                    respHead.setHeader("Content-Encoding", encoding);
                }
            }
        } catch (BOSHException boshx) {
            LOG.log(Level.WARNING,
                    "Could not respond to Accept-Encoding", boshx);
        }
        respHead.setContentLength(data.length);

        HttpResponse response = new HttpResponse(respHead, data);
        if (!httpResp.compareAndSet(null, response)) {
            throw(new IllegalStateException("HTTP Response already sent"));
        }

        if (!resp.compareAndSet(null,
                new StubResponse(respHead, respBody))) {
            throw(new IllegalStateException("Response already sent"));
        }

        synchronized(this) {
            notifyAll();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Package methods:

    public void awaitResponse() {
        synchronized(this) {
            while (resp.get() == null) {
                try {
                    wait();
                } catch (InterruptedException intx) {
                    // Ignore
                }
            }
        }
    }

    public void executeResponse() throws IOException {
        awaitResponse();
        HttpResponse hr = httpResp.getAndSet(null);
        if (hr == null) {
            // Already executed the response
            return;
        }
        exchange.send(hr);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Private methods:


}
