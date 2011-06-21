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

import com.kenai.jbosh.ComposableBody.Builder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BOSH Client session instance.  Each communication session with a remote
 * connection manager is represented and handled by an instance of this
 * class.  This is the main entry point for client-side communications.
 * To create a new session, a client configuration must first be created
 * and then used to create a client instance:
 * <pre>
 * BOSHClientConfig cfg = BOSHClientConfig.Builder.create(
 *         "http://server:1234/httpbind", "jabber.org")
 *     .setFrom("user@jabber.org")
 *     .build();
 * BOSHClient client = BOSHClient.create(cfg);
 * </pre>
 * Additional client configuration options are available.  See the
 * {@code BOSHClientConfig.Builder} class for more information.
 * <p/>
 * Once a {@code BOSHClient} instance has been created, communication with
 * the remote connection manager can begin.  No attempt will be made to
 * establish a connection to the connection manager until the first call
 * is made to the {@code send(ComposableBody)} method.  Note that it is
 * possible to send an empty body to cause an immediate connection attempt
 * to the connection manager.  Sending an empty message would look like
 * the following:
 * <pre>
 * client.send(ComposableBody.builder().build());
 * </pre>
 * For more information on creating body messages with content, see the
 * {@code ComposableBody.Builder} class documentation.
 * <p/>
 * Once a session has been successfully started, the client instance can be
 * used to send arbitrary payload data.  All aspects of the BOSH
 * protocol involving setting and processing attributes in the BOSH
 * namespace will be handled by the client code transparently and behind the
 * scenes.  The user of the client instance can therefore concentrate
 * entirely on the content of the message payload, leaving the semantics of
 * the BOSH protocol to the client implementation.
 * <p/>
 * To be notified of incoming messages from the remote connection manager,
 * a {@code BOSHClientResponseListener} should be added to the client instance.
 * All incoming messages will be published to all response listeners as they
 * arrive and are processed.  As with the transmission of payload data via
 * the {@code send(ComposableBody)} method, there is no need to worry about
 * handling of the BOSH attributes, since this is handled behind the scenes.
 * <p/>
 * If the connection to the remote connection manager is terminated (either
 * explicitly or due to a terminal condition of some sort), all connection
 * listeners will be notified.  After the connection has been closed, the
 * client instance is considered dead and a new one must be created in order
 * to resume communications with the remote server.
 * <p/>
 * Instances of this class are thread-safe.
 *
 * @see BOSHClientConfig.Builder
 * @see BOSHClientResponseListener
 * @see BOSHClientConnListener
 * @see ComposableBody.Builder
 */
public final class BOSHClient {

    /**
     * Logger.
     */
    private static final Logger LOG = Logger.getLogger(
            BOSHClient.class.getName());

    /**
     * Value of the 'type' attribute used for session termination.
     */
    private static final String TERMINATE = "terminate";
    
    /**
     * Value of the 'type' attribute used for recoverable errors.
     */
    private static final String ERROR = "error";

    /**
     * Message to use for interrupted exceptions.
     */
    private static final String INTERRUPTED = "Interrupted";

    /**
     * Connection listeners.
     */
    private final Set<BOSHClientConnListener> connListeners =
            new CopyOnWriteArraySet<BOSHClientConnListener>();

    /**
     * Request listeners.
     */
    private final Set<BOSHClientRequestListener> requestListeners =
            new CopyOnWriteArraySet<BOSHClientRequestListener>();

    /**
     * Response listeners.
     */
    private final Set<BOSHClientResponseListener> responseListeners =
            new CopyOnWriteArraySet<BOSHClientResponseListener>();

    /**
     * Lock instance.
     */
    private final Lock lock = new ReentrantLock();

    /**
     * Condition indicating that there are messages to be exchanged.
     */
    private final Condition notEmpty = lock.newCondition();

    /**
     * Condition indicating that there are available slots for sending
     * messages.
     */
    private final Condition notFull = lock.newCondition();

    /**
     * Session configuration.
     */
    private final BOSHClientConfig cfg;

    /**
     * Processor thread runnable instance.
     */
    private final Runnable procRunnable = new Runnable() {
        /**
         * Process incoming messages.
         */
        public void run() {
            processMessages();
        }
    };

    /**
     * HTTPSender instance.
     */
    private final HTTPSender httpSender =
            ServiceLib.loadService(HTTPSender.class);

    /**
     * Storage for test hook implementation.
     */
    private final AtomicReference<ExchangeInterceptor> exchInterceptor =
            new AtomicReference<ExchangeInterceptor>();

    /**
     * Request ID sequence to use for the session.
     */
    private final RequestIDSequence requestIDSeq = new RequestIDSequence();


    /************************************************************
     * The following vars must be accessed via the lock instance.
     */

    /**
     * Thread which is used to process responses from the connection
     * manager.  Becomes null when session is terminated.
     */
    private Thread procThread;

    /**
     * Connection Manager session parameters.  Only available when in a
     * connected state.
     */
    private CMSessionParams cmParams;

    /**
     * List of active/outstanding requests.
     */
    private Queue<HTTPExchange> exchanges = new LinkedList<HTTPExchange>();

    /**
     * Set of RIDs which have been received, for the purpose of sending
     * response acknowledgements.
     */
    private SortedSet<Long> pendingResponseAcks = new TreeSet<Long>();
    
    /**
     * The highest RID that we've already received a response for.  This value
     * is used to implement response acks.
     */
    private Long responseAck = Long.valueOf(-1L);

    /**
     * List of requests which have been made but not yet acknowledged.  This
     * list remains unpopulated if the CM is not acking requests.
     */
    private List<ComposableBody> pendingRequestAcks =
            new ArrayList<ComposableBody>();
    
    ///////////////////////////////////////////////////////////////////////////
    // Classes:

    /**
     * Class used in testing to dynamically manipulate received exchanges
     * at test runtime.
     */
    abstract static class ExchangeInterceptor {
        /**
         * Limit construction.
         */
        ExchangeInterceptor() {
            // Empty;
        }

        /**
         * Hook to manipulate an HTTPExchange as is is about to be processed.
         *
         * @param exch original exchange that would be processed
         * @return replacement exchange instance, or {@code null} to skip
         *  processing of this exchange
         */
        abstract HTTPExchange interceptExchange(final HTTPExchange exch);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Constructors:

    /**
     * Prevent direct construction.
     */
    private BOSHClient(final BOSHClientConfig sessCfg) {
        cfg = sessCfg;
        init();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Public methods:

    /**
     * Create a new BOSH client session using the client configuration
     * information provided.
     *
     * @param clientCfg session configuration
     * @return BOSH session instance
     */
    public static BOSHClient create(final BOSHClientConfig clientCfg) {
        return new BOSHClient(clientCfg);
    }

    /**
     * Get the client configuration that was used to create this client
     * instance.
     *
     * @return client configuration
     */
    public BOSHClientConfig getBOSHClientConfig() {
        return cfg;
    }

    /**
     * Adds a connection listener to the session.
     *
     * @param listener connection listener to add, if not already added
     */
    public void addBOSHClientConnListener(
            final BOSHClientConnListener listener) {
        connListeners.add(listener);
    }

    /**
     * Removes a connection listener from the session.
     *
     * @param listener connection listener to remove, if previously added
     */
    public void removeBOSHClientConnListener(
            final BOSHClientConnListener listener) {
        connListeners.remove(listener);
    }

    /**
     * Adds a request message listener to the session.
     *
     * @param listener request listener to add, if not already added
     */
    public void addBOSHClientRequestListener(
            final BOSHClientRequestListener listener) {
        requestListeners.add(listener);
    }

    /**
     * Removes a request message listener from the session, if previously
     * added.
     *
     * @param listener instance to remove
     */
    public void removeBOSHClientRequestListener(
            final BOSHClientRequestListener listener) {
        requestListeners.remove(listener);
    }

    /**
     * Adds a response message listener to the session.
     *
     * @param listener response listener to add, if not already added
     */
    public void addBOSHClientResponseListener(
            final BOSHClientResponseListener listener) {
        responseListeners.add(listener);
    }

    /**
     * Removes a response message listener from the session, if previously
     * added.
     *
     * @param listener instance to remove
     */
    public void removeBOSHClientResponseListener(
            final BOSHClientResponseListener listener) {
        responseListeners.remove(listener);
    }

    /**
     * Send the provided message data to the remote connection manager.  The
     * provided message body does not need to have any BOSH-specific attribute
     * information set.  It only needs to contain the actual message payload
     * that should be delivered to the remote server.
     * <p/>
     * The first call to this method will result in a connection attempt
     * to the remote connection manager.  Subsequent calls to this method
     * will block until the underlying session state allows for the message
     * to be transmitted.  In certain scenarios - such as when the maximum
     * number of outbound connections has been reached - calls to this method
     * will block for short periods of time.
     *
     * @param body message data to send to remote server
     * @throws BOSHException on message transmission failure
     */
    public void send(final ComposableBody body) throws BOSHException {
        HTTPExchange exch;
        CMSessionParams params;
        lock.lock();
        try {
            blockUntilSendable(body);
            if (!isWorking() && !isTermination(body)) {
                throw(new BOSHException(
                        "Cannot send message when session is closed"));
            }
            
            long rid = requestIDSeq.getNextRID();
            ComposableBody request = body;
            params = cmParams;
            if (params == null && exchanges.isEmpty()) {
                // This is the first message being sent
                request = applySessionCreationRequest(rid, body);
            } else {
                request = applySessionData(rid, body);
                if (cmParams.isAckingRequests()) {
                    pendingRequestAcks.add(request);
                }
            }
            exch = new HTTPExchange(request);
            exchanges.add(exch);
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
        AbstractBody finalReq = exch.getRequest();
        fireRequestSent(finalReq);
        HTTPResponse resp = httpSender.send(params, finalReq);
        exch.setHTTPResponse(resp);
    }

    /**
     * End the BOSH session by disconnecting from the remote BOSH connection
     * manager.
     *
     * @throws BOSHException when termination message cannot be sent
     */
    public void disconnect() throws BOSHException {
        disconnect(ComposableBody.builder().build());
    }

    /**
     * End the BOSH session by disconnecting from the remote BOSH connection
     * manager, sending the provided content in the final connection
     * termination message.
     *
     * @param msg final message to send
     * @throws BOSHException when termination message cannot be sent
     */
    public void disconnect(final ComposableBody msg) throws BOSHException {
        Builder builder = msg.rebuild();
        builder.setAttribute(Attributes.TYPE, TERMINATE);
        send(builder.build());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Package-private methods:

    /**
     * Get the current CM session params.
     *
     * @return current session params, or {@code null}
     */
    CMSessionParams getCMSessionParams() {
        lock.lock();
        try {
            return cmParams;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wait until no more messages are waiting to be processed.
     */
    void drain() {
        lock.lock();
        try {
            while (!exchanges.isEmpty()) {
                try {
                    notFull.await();
                } catch (InterruptedException intx) {
                    LOG.log(Level.FINEST, INTERRUPTED, intx);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Test method used to forcibly discard next exchange.
     *
     * @param interceptor exchange interceptor
     */
    void setExchangeInterceptor(final ExchangeInterceptor interceptor) {
        exchInterceptor.set(interceptor);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Private methods:

    /**
     * Initialize the session.  This initializes the underlying HTTP
     * transport implementation and starts the receive thread.
     */
    private void init() {
        lock.lock();
        try {
            httpSender.init(cfg);
            procThread = new Thread(procRunnable);
            procThread.setDaemon(true);
            procThread.setName(BOSHClient.class.getSimpleName()
                    + "[" + System.identityHashCode(this)
                    + "]: Receive thread");
            procThread.start();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Destroy this session.
     *
     * @param cause the reason for the session termination, or {@code null}
     *  for normal termination
     */
    private void dispose(final Throwable cause) {
        lock.lock();
        try {
            notEmpty.signalAll();
            notFull.signalAll();
            procThread = null;
        } finally {
            lock.unlock();
        }
        httpSender.destroy();
        if (cause == null) {
            fireConnectionClosed();
        } else {
            fireConnectionClosedOnError(cause);
        }
    }

    /**
     * Determines if the message body specified indicates a request to
     * pause the session.
     *
     * @param msg message to evaluate
     * @return {@code true} if the message is a pause request, {@code false}
     *  otherwise
     */
    private boolean isPause(final AbstractBody msg) {
        return msg.getAttribute(Attributes.PAUSE) != null;
    }
    
    /**
     * Determines if the message body specified indicates a termination of
     * the session.
     *
     * @param msg message to evaluate
     * @return {@code true} if the message is a session termination,
     *  {@code false} otherwise
     */
    private boolean isTermination(final AbstractBody msg) {
        return TERMINATE.equals(msg.getAttribute(Attributes.TYPE));
    }

    /**
     * Evaluates the HTTP response code and response message and returns the
     * terminal binding condition that it describes, if any.
     *
     * @param respCode HTTP response code
     * @param respBody response body
     * @return terminal binding condition, or {@code null} if not a terminal
     *  binding condition message
     */
    private TerminalBindingCondition getTerminalBindingCondition(
            final int respCode,
            final AbstractBody respBody) {
        if (isTermination(respBody)) {
            String str = respBody.getAttribute(Attributes.CONDITION);
            return TerminalBindingCondition.forString(str);
        }
        // Check for deprecated HTTP Error Conditions
        if (cmParams != null && cmParams.getVersion() == null) {
            return TerminalBindingCondition.forHTTPResponseCode(respCode);
        }
        return null;
    }

    /**
     * Determines if the message specified is immediately sendable or if it
     * needs to block until the session state changes.
     *
     * @param msg message to evaluate
     * @return {@code true} if the message can be immediately sent,
     *  {@code false} otherwise
     */
    private boolean isImmediatelySendable(final AbstractBody msg) {
        if (cmParams == null) {
            // block if we're waiting for a response to our first request
            return exchanges.isEmpty();
        }

        AttrRequests requests = cmParams.getRequests();
        if (requests == null) {
            return true;
        }
        int maxRequests = requests.intValue();
        if (exchanges.size() < maxRequests) {
            return true;
        }
        if (exchanges.size() == maxRequests
                && (isTermination(msg) || isPause(msg))) {
            // One additional terminate or pause message is allowed
            return true;
        }
        return false;
    }

    /**
     * Determines whether or not the session is still active.
     *
     * @return {@code true} if it is, {@code false} otherwise
     */
    private boolean isWorking() {
        return procThread != null;
    }

    /**
     * Blocks until either the message provided becomes immediately
     * sendable or until the session is terminated.
     *
     * @param msg message to evaluate
     */
    private void blockUntilSendable(final AbstractBody msg) {
        while (isWorking() && !isImmediatelySendable(msg)) {
            try {
                notFull.await();
            } catch (InterruptedException intx) {
                LOG.log(Level.FINEST, INTERRUPTED, intx);
            }
        }
    }

    /**
     * Modifies the specified body message such that it becomes a new
     * BOSH session creation request.
     *
     * @param rid request ID to use
     * @param orig original body to modify
     * @return modified message which acts as a session creation request
     */
    private ComposableBody applySessionCreationRequest(
            final long rid, final ComposableBody orig) throws BOSHException {
        Builder builder = orig.rebuild();
        builder.setAttribute(Attributes.TO, cfg.getTo());
        builder.setAttribute(Attributes.XML_LANG, cfg.getLang());
        builder.setAttribute(Attributes.VER,
                AttrVersion.getSupportedVersion().toString());
        builder.setAttribute(Attributes.WAIT, "60");
        builder.setAttribute(Attributes.HOLD, "1");
        builder.setAttribute(Attributes.RID, Long.toString(rid));
        applyRoute(builder);
        applyFrom(builder);
        builder.setAttribute(Attributes.ACK, "1");

        // Make sure the following are NOT present (i.e., during retries)
        builder.setAttribute(Attributes.SID, null);
        return builder.build();
    }

    /**
     * Applies routing information to the request message who's builder has
     * been provided.
     *
     * @param builder builder instance to add routing information to
     */
    private void applyRoute(final Builder builder) {
        String route = cfg.getRoute();
        if (route != null) {
            builder.setAttribute(Attributes.ROUTE, route);
        }
    }

    /**
     * Applies the local station ID information to the request message who's
     * builder has been provided.
     *
     * @param builder builder instance to add station ID information to
     */
    private void applyFrom(final Builder builder) {
        String from = cfg.getFrom();
        if (from != null) {
            builder.setAttribute(Attributes.FROM, from);
        }
    }

    /**
     * Applies existing session data to the outbound request, returning the
     * modified request.
     *
     * This method assumes the lock is currently held.
     *
     * @param rid request ID to use
     * @param orig original/raw request
     * @return modified request with session information applied
     */
    private ComposableBody applySessionData(
            final long rid,
            final ComposableBody orig) throws BOSHException {
        Builder builder = orig.rebuild();
        builder.setAttribute(Attributes.SID,
                cmParams.getSessionID().toString());
        builder.setAttribute(Attributes.RID, Long.toString(rid));
        applyResponseAcknowledgement(builder, rid);
        return builder.build();
    }

    /**
     * Sets the 'ack' attribute of the request to the value of the highest
     * 'rid' of a request for which it has already received a response in the
     * case where it has also received all responses associated with lower
     * 'rid' values.  The only exception is that, after its session creation
     * request, the client SHOULD NOT include an 'ack' attribute in any request
     * if it has received responses to all its previous requests.
     *
     * @param builder message builder
     * @param rid current request RID
     */
    private void applyResponseAcknowledgement(
            final Builder builder,
            final long rid) {
        if (responseAck.equals(Long.valueOf(-1L))) {
            // We have not received any responses yet
            return;
        }

        Long prevRID = Long.valueOf(rid - 1L);
        if (responseAck.equals(prevRID)) {
            // Implicit ack
            return;
        }
        
        builder.setAttribute(Attributes.ACK, responseAck.toString());
    }

    /**
     * While we are "connected", process received responses.
     *
     * This method is run in the processing thread.
     */
    private void processMessages() {
        LOG.log(Level.FINEST, "Processing thread starting");
        try {
            HTTPExchange exch;
            do {
                exch = nextExchange();
                if (exch == null) {
                    break;
                }

                // Test hook to manipulate what the client sees:
                ExchangeInterceptor interceptor = exchInterceptor.get();
                if (interceptor != null) {
                    HTTPExchange newExch = interceptor.interceptExchange(exch);
                    if (newExch == null) {
                        LOG.log(Level.FINE, "Discarding exchange on request "
                                + "of test hook: RID="
                                + exch.getRequest().getAttribute(
                                    Attributes.RID));
                        continue;
                    }
                    exch = newExch;
                }

                processExchange(exch);
            } while (true);
        } finally {
            LOG.log(Level.FINEST, "Processing thread exiting");
        }

    }

    /**
     * Get the next message exchange to process, blocking until one becomes
     * available if nothing is already waiting for processing.
     *
     * @return next available exchange to process, or {@code null} if no
     *  exchanges are immediately available
     */
    private HTTPExchange nextExchange() {
        final Thread thread = Thread.currentThread();
        HTTPExchange exch = null;
        lock.lock();
        try {
            do {
                if (!thread.equals(procThread)) {
                    break;
                }
                exch = exchanges.peek();
                if (exch == null) {
                    try {
                        notEmpty.await();
                    } catch (InterruptedException intx) {
                        LOG.log(Level.FINEST, INTERRUPTED, intx);
                    }
                }
            } while (exch == null);
        } finally {
            lock.unlock();
        }
        return exch;
    }

    /**
     * Process the next, provided exchange.  This is the main processing
     * method of the receive thread.
     *
     * @param exch message exchange to process
     */
    private void processExchange(final HTTPExchange exch) {
        HTTPResponse resp;
        AbstractBody body;
        int respCode;
        try {
            resp = exch.getHTTPResponse();
            body = resp.getBody();
            respCode = resp.getHTTPStatus();
        } catch (BOSHException boshx) {
            LOG.log(Level.WARNING, "Could not obtain response", boshx);
            dispose(boshx);
            return;
        } catch (InterruptedException intx) {
            LOG.log(Level.FINEST, INTERRUPTED, intx);
            dispose(intx);
            return;
        }
        fireResponseReceived(body);

        // Process the message with the current session state
        AbstractBody req = exch.getRequest();
        CMSessionParams params;
        HTTPExchange resend = null;
        boolean sessionEstablished = false;
        lock.lock();
        try {
            // Check for session creation response info, if needed
            if (cmParams == null) {
                cmParams = CMSessionParams.fromSessionInit(req, body);
                sessionEstablished = true;
            }
            params = cmParams;

            checkForTerminalBindingConditions(body, respCode);
            if (isTermination(body)) {
                // Explicit termination
                dispose(null);
            } else {
                if (isRecoverableBindingCondition(body)) {
                    // Retransmit outstanding requests
                } else {
                    // Process message as normal
                    processRequestAcknowledgements(req, body);
                    processResponseAcknowledgementData(req);
                    resend = processResponseAcknowledgementReport(body);
                }
            }
        } catch (BOSHException boshx) {
            LOG.log(Level.FINEST, "Could not process response", boshx);
            dispose(boshx);
            return;
        } finally {
            try {
                exchanges.remove(exch);
                notFull.signalAll();
            } finally {
                lock.unlock();
            }
        }

        if (sessionEstablished) {
            fireConnectionEstablished();
        }

        if (resend != null) {
            AbstractBody finalReq = resend.getRequest();
            fireRequestSent(finalReq);
            HTTPResponse response = httpSender.send(params, finalReq);
            resend.setHTTPResponse(response);
        }
    }

    /**
     * Checks to see if the response indicates a terminal binding condition
     * (as per XEP-0124 section 17).  If it does, an exception is thrown.
     *
     * @param body response body to evaluate
     * @param code HTTP response code
     * @throws BOSHException if a terminal binding condition is detected
     */
    private void checkForTerminalBindingConditions(
            final AbstractBody body,
            final int code)
            throws BOSHException {
        TerminalBindingCondition cond =
                getTerminalBindingCondition(code, body);
        if (cond != null) {
            throw(new BOSHException(
                    "Terminal binding condition encountered: "
                    + cond.getCondition() + "  ("
                    + cond.getMessage() + ")"));
        }
    }

    /**
     * Determines whether or not the response indicates a recoverable
     * binding condition (as per XEP-0124 section 17).
     *
     * @param resp response body
     * @return {@code true} if it does, {@code false} otherwise
     */
    private boolean isRecoverableBindingCondition(
            final AbstractBody resp) {
        return ERROR.equals(resp.getAttribute(Attributes.TYPE));
    }

    /**
     * Check the response for request acknowledgements and take appropriate
     * action.
     *
     * This method assumes the lock is currently held.
     *
     * @param req request
     * @param resp response
     */
    private void processRequestAcknowledgements(
            final AbstractBody req, final AbstractBody resp) {
        if (!cmParams.isAckingRequests()) {
            return;
        }

        // If a report or time attribute is set, we aren't acking anything
        if (resp.getAttribute(Attributes.REPORT) != null) {
            return;
        }

        // Figure out what the highest acked RID is
        String acked = resp.getAttribute(Attributes.ACK);
        Long ackUpTo;
        if (acked == null) {
            // Implicit ack of all prior requests up until RID
            ackUpTo = Long.parseLong(req.getAttribute(Attributes.RID));
        } else {
            ackUpTo = Long.parseLong(acked);
        }

        // Remove the acked requests from the list
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Removing pending acks up to: " + ackUpTo);
        }
        Iterator<ComposableBody> iter = pendingRequestAcks.iterator();
        while (iter.hasNext()) {
            AbstractBody pending = iter.next();
            Long pendingRID = Long.parseLong(
                    pending.getAttribute(Attributes.RID));
            if (pendingRID.compareTo(ackUpTo) <= 0) {
                iter.remove();
            }
        }
    }

    /**
     * Process the response in order to update the response acknowlegement
     * data.
     *
     * This method assumes the lock is currently held.
     *
     * @param req request
     */
    private void processResponseAcknowledgementData(
            final AbstractBody req) {
        Long rid = Long.parseLong(req.getAttribute(Attributes.RID));
        if (responseAck.equals(Long.valueOf(-1L))) {
            // This is the first request
            responseAck = rid;
        } else {
            pendingResponseAcks.add(rid);
            // Remove up until the first missing response (or end of queue)
            Long whileVal = responseAck;
            while (whileVal.equals(pendingResponseAcks.first())) {
                responseAck = whileVal;
                pendingResponseAcks.remove(whileVal);
                whileVal = Long.valueOf(whileVal.longValue() + 1);
            }
        }
    }

    /**
     * Process the response in order to check for and respond to any potential
     * ack reports.
     *
     * This method assumes the lock is currently held.
     *
     * @param resp response
     * @return exchange to transmit if a resend is to be performed, or
     *  {@code null} if no resend is necessary
     * @throws BOSHException when a a retry is needed but cannot be performed
     */
    private HTTPExchange processResponseAcknowledgementReport(
            final AbstractBody resp)
            throws BOSHException {
        String reportStr = resp.getAttribute(Attributes.REPORT);
        if (reportStr == null) {
            // No report on this message
            return null;
        }
        
        Long report = Long.parseLong(reportStr);
        Long time = Long.parseLong(resp.getAttribute(Attributes.TIME));
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Received report of missing request (RID="
                    + report + ", time=" + time + "ms)");
        }

        // Find the missing request
        Iterator<ComposableBody> iter = pendingRequestAcks.iterator();
        AbstractBody req = null;
        while (iter.hasNext() && req == null) {
            AbstractBody pending = iter.next();
            Long pendingRID = Long.parseLong(
                    pending.getAttribute(Attributes.RID));
            if (report.equals(pendingRID)) {
                req = pending;
            }
        }

        if (req == null) {
            throw(new BOSHException("Report of missing message with RID '"
                    + reportStr
                    + "' but local copy of that request was not found"));
        }

        // Resend the missing request
        HTTPExchange exch = new HTTPExchange(req);
        exchanges.add(exch);
        notEmpty.signalAll();
        return exch;
    }

    /**
     * Notifies all request listeners that the specified request is being
     * sent.
     *
     * @param request request being sent
     */
    private void fireRequestSent(final AbstractBody request) {
        BOSHMessageEvent event = null;
        for (BOSHClientRequestListener listener : requestListeners) {
            if (event == null) {
                event = BOSHMessageEvent.createRequestSentEvent(this, request);
            }
            listener.requestSent(event);
        }
    }

    /**
     * Notifies all response listeners that the specified response has been
     * received.
     *
     * @param response response received
     */
    private void fireResponseReceived(final AbstractBody response) {
        BOSHMessageEvent event = null;
        for (BOSHClientResponseListener listener : responseListeners) {
            if (event == null) {
                event = BOSHMessageEvent.createResponseReceivedEvent(
                        this, response);
            }
            listener.responseReceived(event);
        }
    }

    /**
     * Notifies all connection listeners that the session has been successfully
     * established.
     */
    private void fireConnectionEstablished() {
        BOSHClientConnEvent event = null;
        for (BOSHClientConnListener listener : connListeners) {
            if (event == null) {
                event = BOSHClientConnEvent
                        .createConnectionEstablishedEvent(this);
            }
            listener.connectionEvent(event);
        }
    }

    /**
     * Notifies all connection listeners that the session has been
     * terminated normally.
     */
    private void fireConnectionClosed() {
        BOSHClientConnEvent event = null;
        for (BOSHClientConnListener listener : connListeners) {
            if (event == null) {
                event = BOSHClientConnEvent.createConnectionClosedEvent(this);
            }
            listener.connectionEvent(event);
        }
    }

    /**
     * Notifies all connection listeners that the session has been
     * terminated due to the exceptional condition provided.
     *
     * @param cause cause of the termination
     */
    private void fireConnectionClosedOnError(
            final Throwable cause) {
        BOSHClientConnEvent event = null;
        for (BOSHClientConnListener listener : connListeners) {
            if (event == null) {
                event = BOSHClientConnEvent
                        .createConnectionClosedOnErrorEvent(
                        this, pendingRequestAcks, cause);
            }
            listener.connectionEvent(event);
        }
    }

}
