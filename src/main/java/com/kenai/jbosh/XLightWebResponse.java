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
import org.xlightweb.IFutureResponse;
import org.xlightweb.IHttpResponse;
import org.xlightweb.PostRequest;
import org.xlightweb.client.HttpClient;

final class XLightWebResponse implements HTTPResponse {

    /**
     * Name of the accept encoding header.
     */
    private static final String ACCEPT_ENCODING = "Accept-Encoding";

    /**
     * Value to use for the ACCEPT_ENCODING header.
     */
    private static final String ACCEPT_ENCODING_VAL =
            ZLIBCodec.getID() + ", " + GZIPCodec.getID();

    /**
     * Name of the content encoding header.
     */
    private static final String CONTENT_ENCODING = "Content-Encoding";

    /**
     * Name of the character set to encode the body to/from.
     */
    private static final String CHARSET = "UTF-8";

    /**
     * Content type to use when transmitting the body data.
     */
    private static final String CONTENT_TYPE = "text/xml; charset=utf-8";

    /**
     * Lock used for internal synchronization.
     */
    private final Lock lock = new ReentrantLock();

    /**
     * Response future that we'll pull our results from.
     */
    private final IFutureResponse future;

    /**
     * The response after it's been pulled from the future, or {@code null}
     * if that has not yet happened.
     */
    private IHttpResponse httpResp;

    /**
     * The response body after it's been pulled from the future, or {@code null}
     * if that has not yet happened.
     */
    private AbstractBody resp;

    /**
     * Exception to throw when the response data is attempted to be accessed,
     * or {@code null} if no exception should be thrown.
     */
    private BOSHException toThrow;

    /**
     * Create and send a new request to the upstream connection manager,
     * providing deferred access to the results to be returned.
     *
     * @param client client instance to use when sending the request
     * @param cfg client configuration
     * @param params connection manager parameters from the session creation
     *  response, or {@code null} if the session has not yet been established
     * @param request body of the client request
     */
    XLightWebResponse(
            final HttpClient client,
            final BOSHClientConfig cfg,
            final CMSessionParams params,
            final AbstractBody request) {
        super();

        IFutureResponse futureVal;
        try {
            String xml = request.toXML();
            byte[] data = xml.getBytes(CHARSET);

            String encoding = null;
            if (cfg.isCompressionEnabled() && params != null) {
                AttrAccept accept = params.getAccept();
                if (accept != null) {
                    if (accept.isAccepted(ZLIBCodec.getID())) {
                        encoding = ZLIBCodec.getID();
                        data = ZLIBCodec.encode(data);
                    } else if (accept.isAccepted(GZIPCodec.getID())) {
                        encoding = GZIPCodec.getID();
                        data = GZIPCodec.encode(data);
                    }
                }
            }

            PostRequest post = new PostRequest(
                    cfg.getURI().toString(), CONTENT_TYPE, data);
            if (encoding != null) {
                post.setHeader(CONTENT_ENCODING, encoding);
            }
            post.setTransferEncoding(null);
            post.setContentLength(data.length);
            if (cfg.isCompressionEnabled()) {
                post.setHeader(ACCEPT_ENCODING, ACCEPT_ENCODING_VAL);
            }
            futureVal = client.send(post);
        } catch (IOException iox) {
            toThrow = new BOSHException("Could not send request", iox);
            futureVal = null;
        }
        future = futureVal;
    }

    /**
     * Abort the client transmission and response processing.
     */
    public void abort() {
        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * Wait for and then return the response HTTP status.
     *
     * @return http status of the response
     * @throws InterruptedException if interrupted while awaiting the response
     * @throws BOSHException on communication cfailure
     */
    public int getHTTPStatus() throws InterruptedException, BOSHException {
        awaitResponse();
        return httpResp.getStatus();
    }

    /**
     * Wait for and then return the response body.
     *
     * @return body of the response
     * @throws InterruptedException if interrupted while awaiting the response
     * @throws BOSHException on communication cfailure
     */
    public AbstractBody getBody() throws InterruptedException, BOSHException {
        awaitResponse();
        return resp;
    }

    /**
     * Await the response, storing the result in the instance variables of
     * this class when they arrive.
     *
     * @throws InterruptedException if interrupted while awaiting the response
     * @throws BOSHException on communicationf ailure
     */
    private void awaitResponse() throws InterruptedException, BOSHException {
        lock.lock();
        try {
            if (toThrow != null) {
                throw(toThrow);
            }
            if (httpResp != null) {
                return;
            }
            httpResp = future.getResponse();
            byte[] data = httpResp.getBlockingBody().readBytes();
            String encoding = httpResp.getHeader(CONTENT_ENCODING);
            if (ZLIBCodec.getID().equalsIgnoreCase(encoding)) {
                data = ZLIBCodec.decode(data);
            } else if (GZIPCodec.getID().equalsIgnoreCase(encoding)) {
                data = GZIPCodec.decode(data);
            }
            String bodyStr = new String(data, CHARSET);
            resp = StaticBody.fromString(bodyStr);
        } catch (IOException iox) {
            toThrow = new BOSHException("Could not obtain response", iox);
            throw(toThrow);
        } finally {
            lock.unlock();
        }
    }

}
