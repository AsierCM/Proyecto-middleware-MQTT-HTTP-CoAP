/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.eclipse.californium.core;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.californium.core.coap.BlockOption;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.util.NamedThreadFactory;

/**
 * The PubSubClient to perform the operations specified in the PubSubAPI
 * @author Thomas Wiss
 */
public class CoapPubSubClient implements CoapPubSubAPI {

    /**
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(CoapPubSubClient.class.getCanonicalName());

    /**
     * The timeout.
     */
    private long timeout = NetworkConfig.getStandard().getLong(NetworkConfig.Keys.EXCHANGE_LIFETIME);

    /**
     * The destination URI
     */
    private String uri;

    /**
     * The type used for requests (CON is default)
     */
    private CoAP.Type type = CoAP.Type.CON;

    private int blockwise = 0;

    /**
     * The client-specific executor service.
     */
    private ExecutorService executor;

    /**
     * The endpoint.
     */
    private Endpoint endpoint;

    /**
     * Map with subscribe request-token matching a certain topic-URI
     */
    private ConcurrentHashMap<String, byte[]> subscribeToken = new ConcurrentHashMap();

    /**
     * Constructs a new CoapClient that has no destination URI yet.
     */
    public CoapPubSubClient() {
        this("");
    }

    /**
     * Constructs a new CoapClient that sends requests to the specified URI.
     *
     * @param uri the uri
     */
    public CoapPubSubClient(String uri) {
        this.uri = uri;
    }

    /**
     * Constructs a new CoapClient that sends request to the specified URI.
     *
     * @param uri the uri
     */
    public CoapPubSubClient(URI uri) {
        this(uri.toString());
    }

    /**
     * Constructs a new CoapClient with the specified scheme, host, port and
     * path as URI.
     *
     * @param scheme the scheme
     * @param host the host
     * @param port the port
     * @param path the path
     */
    public CoapPubSubClient(String scheme, String host, int port, String... path) {
        StringBuilder builder = new StringBuilder()
                .append(scheme).append("://").append(host).append(":").append(port);
        for (String element : path) {
            builder.append("/").append(element);
        }
        this.uri = builder.toString();
    }

    /**
     * Gets the maximum amount of time that synchronous method calls will block
     * and wait.
     * <p>
     * The default value of this property is read from configuration property
     * {@link org.eclipse.californium.core.network.config.NetworkConfig.Keys#EXCHANGE_LIFETIME}.
     *
     * @return The timeout in milliseconds.
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets the maximum amount of time that synchronous method calls will block
     * and wait. Setting this property to 0 will result in methods waiting
     * infinitely.
     * <p>
     * The default value of this property is read from configuration property
     * {@link org.eclipse.californium.core.network.config.NetworkConfig.Keys#EXCHANGE_LIFETIME}.
     * <p>
     * Under normal circumstances this property should be set to at least the
     * <em>EXCHANGE_LIFETIME</em>
     * (the default) in order to account for potential retransmissions of
     * request and response messages.
     * <p>
     * When running over DTLS and the client is behind a NAT firewall, requests
     * may frequently fail (run into timeout) due to the fact that the client
     * has been assigned a new IP address or port by the firewall and the peer
     * can no longer associate this client's address with the original DTLS
     * session. In such cases it might be worthwhile to set the value of this
     * property to a smaller number so that the timeout is detected sooner and a
     * new session can be negotiated.
     *
     * @param timeout The timeout in milliseconds.
     * @return This CoAP client for command chaining.
     */
    public CoapPubSubClient setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Gets the destination URI of this client.
     *
     * @return the uri
     */
    public String getURI() {
        return uri;
    }

    /**
     * Sets the destination URI of this client.
     *
     * @param uri the uri
     * @return the CoAP client
     */
    public CoapPubSubClient setURI(String uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Sets a single-threaded executor to this client. All handlers will be
     * invoked by this executor. Note that the client executor uses a user
     * thread (not a daemon thread) that needs to be stopped to exit the
     * program.
     *
     * @return the CoAP client
     */
    public CoapPubSubClient useExecutor() {
        this.executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("CoapClient#")); //$NON-NLS-1$

        // activates the executor so that this user thread starts deterministically
        executor.execute(new Runnable() {
            public void run() {
                LOGGER.config("Using a SingleThreadExecutor for the CoapClient");
            }
        ;
        });
		
		return this;
    }

    /**
     * Sets the executor service for this client. All handlers will be invoked
     * by this executor.
     *
     * @param executor the executor service
     * @return the CoAP client
     */
    public CoapPubSubClient setExecutor(ExecutorService executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Gets the endpoint this client uses.
     *
     * @return the endpoint
     */
    public Endpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Sets the endpoint this client is supposed to use.
     *
     * @param endpoint the endpoint
     * @return the CoAP client
     */
    public CoapPubSubClient setEndpoint(Endpoint endpoint) {

        this.endpoint = endpoint;

        if (!endpoint.isStarted()) {
            try {
                endpoint.start();
                LOGGER.log(Level.INFO, "Started set client endpoint {0}", endpoint.getAddress());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not set and start client endpoint", e);
            }

        }

        return this;
    }

    /**
     * Let the client use Confirmable requests.
     *
     * @return the CoAP client
     */
    public CoapPubSubClient useCONs() {
        this.type = CoAP.Type.CON;
        return this;
    }

    /**
     * Let the client use Non-Confirmable requests.
     *
     * @return the CoAP client
     */
    public CoapPubSubClient useNONs() {
        this.type = CoAP.Type.NON;
        return this;
    }

    /**
     * Let the client use early negotiation for the blocksize (16, 32, 64, 128,
     * 256, 512, or 1024). Other values will be matched to the closest logarithm
     * dualis.
     *
     * @param size the preferred block size
     * @return the CoAP client
     */
    public CoapPubSubClient useEarlyNegotiation(int size) {
        this.blockwise = size;
        return this;
    }

    /**
     * Let the client use late negotiation for the block size (default).
     *
     * @return the CoAP client
     */
    public CoapPubSubClient useLateNegotiation() {
        this.blockwise = 0;
        return this;
    }

    /**
     * Performs a CoAP ping using the default timeout for requests.
     *
     * @return success of the ping
     */
    public boolean ping() {
        return ping(this.timeout);
    }

    /**
     * Performs a CoAP ping and gives up after the given number of milliseconds.
     *
     * @param timeout the time to wait for a pong in ms
     * @return success of the ping
     */
    public boolean ping(long timeout) {
        try {
            Request request = new Request(null, CoAP.Type.CON);
            request.setToken(new byte[0]);
            request.setURI(uri);
            request.send().waitForResponse(timeout);
            return request.isRejected();
        } catch (InterruptedException e) {
            // waiting was interrupted, which is fine
        }
        return false;
    }

    @Override
    public Set<WebLink> discover() {
        return discover(null);
    }

    @Override
    public Set<WebLink> discover(String query) {
        Request discover = newGet();
        discover.setURI(uri); // for scheme and authority, but then remove path and query
        discover.getOptions().clearUriPath().clearUriQuery().setUriPath("/ps/");
        if (query != null) {
            discover.getOptions().setUriQuery(query);
        }
        CoapResponse links = synchronous(discover);

        // if no response, return null (e.g., timeout)
        if (links == null) {
            return null;
        }

        // check if Link Format
        if (links.getOptions().getContentFormat() != MediaTypeRegistry.APPLICATION_LINK_FORMAT) {
            return Collections.emptySet();
        }

        // parse and return
        return LinkFormat.parse(links.getResponseText());
    }

    @Override
    public CoapResponse create(byte[] payload, int contentFormat) {
        return synchronous(format(newPost().setURI(uri).setPayload(payload), 
                PubSubValidator.validateContentFormat(contentFormat)));
    }

    @Override
    public CoapResponse create(byte[] payload, int contentFormat, long maxAgeOfTopic) {
        Request request = new Request(CoAP.Code.POST);
        request.getOptions().setMaxAge(maxAgeOfTopic);
        request.setURI(uri).setPayload(payload);
        return synchronous(format(request, PubSubValidator.validateContentFormat(contentFormat)));

    }

    @Override
    public CoapResponse create(String payload, int contentFormat) {
        return synchronous(format(newPost().setURI(uri).setPayload(payload),
                PubSubValidator.validateContentFormat(contentFormat)));
    }

    @Override
    public CoapResponse create(String payload, int contentFormat, long maxAgeOfTopic) {
        Request request = new Request(CoAP.Code.POST);
        request.getOptions().setMaxAge(maxAgeOfTopic);
        request.setURI(uri).setPayload(payload);
        return synchronous(format(request, PubSubValidator.validateContentFormat(contentFormat)));
    }

    @Override
    public void create(CoapHandler handler, byte[] payload, int contentFormat) {
        asynchronous(format(newPost().setURI(uri).setPayload(payload), 
                PubSubValidator.validateContentFormat(contentFormat)), handler);
    }

    @Override
    public void create(CoapHandler handler, String payload, int contentFormat) {
        asynchronous(format(newPost().setURI(uri).setPayload(payload), 
                PubSubValidator.validateContentFormat(contentFormat)), handler);
    }

    @Override
    public void create(CoapHandler handler, byte[] payload, int contentFormat, long maxAgeOfTopic) {
        Request request = new Request(CoAP.Code.POST);
        request.getOptions().setMaxAge(maxAgeOfTopic);
        request.setURI(uri).setPayload(payload);
        asynchronous(format(request, PubSubValidator.validateContentFormat(contentFormat)), handler);
    }

    @Override
    public void create(CoapHandler handler, String payload, int contentFormat, long maxAgeOfTopic) {
        Request request = new Request(CoAP.Code.POST);
        request.getOptions().setMaxAge(maxAgeOfTopic);
        request.setURI(uri).setPayload(payload);
        asynchronous(format(request, PubSubValidator.validateContentFormat(contentFormat)), handler);
    }

    @Override
    public CoapResponse publish(byte[] payload, int contentFormat) {
        return synchronous(format(newPut().setURI(uri).setPayload(payload), 
                PubSubValidator.validateContentFormat(contentFormat)));
    }

    @Override
    public CoapResponse publish(byte[] payload, int contentFormat, long maxAgeOfTopic) {
        Request request = new Request(CoAP.Code.PUT);
        request.getOptions().setMaxAge(maxAgeOfTopic);
        request.setURI(uri).setPayload(payload);
        return synchronous(format(request, PubSubValidator.validateContentFormat(contentFormat)));
    }

    @Override
    public CoapResponse publish(String payload, int contentFormat) {
        return synchronous(format(newPut().setURI(uri).setPayload(payload), contentFormat));
    }

    @Override
    public CoapResponse publish(String payload, int contentFormat, long maxAgeOfTopic) {
        Request request = new Request(CoAP.Code.PUT);
        request.getOptions().setMaxAge(maxAgeOfTopic);
        request.setURI(uri).setPayload(payload);
        return synchronous(format(request, PubSubValidator.validateContentFormat(contentFormat)));
    }

    @Override
    public void publish(CoapHandler handler, byte[] payload, int contentFormat) {
        asynchronous(format(newPost().setURI(uri).setPayload(payload), 
                PubSubValidator.validateContentFormat(contentFormat)), handler);
    }

    @Override
    public void publish(CoapHandler handler, String payload, int contentFormat) {
        asynchronous(format(newPost().setURI(uri).setPayload(payload), 
                PubSubValidator.validateContentFormat(contentFormat)), handler);
    }

    @Override
    public void publish(CoapHandler handler, byte[] payload, int contentFormat, long maxAgeOfTopic) {
        Request request = new Request(CoAP.Code.PUT);
        request.getOptions().setMaxAge(maxAgeOfTopic);
        request.setURI(uri).setPayload(payload);
        asynchronous(format(request, PubSubValidator.validateContentFormat(contentFormat)), handler);
    }

    @Override
    public void publish(CoapHandler handler, String payload, int contentFormat, long maxAgeOfTopic) {
        Request request = new Request(CoAP.Code.PUT);
        request.getOptions().setMaxAge(maxAgeOfTopic);
        request.setURI(uri).setPayload(payload);
        asynchronous(format(request, PubSubValidator.validateContentFormat(contentFormat)), handler);
    }

    @Override
    public CoapPubSubObserveRelation subscribe(CoapHandler handler, int contentFormat) {
        Request request = format(newGet().setURI(uri).setObserve(), 
                PubSubValidator.validateContentFormat(contentFormat));
        return handleSubscribe(request, handler);
    }

    @Override
    public CoapResponse unsubscribe() {
        return handleUnsubscribe();
    }

    @Override
    public CoapResponse read(int contentFormat) {
        return synchronous(format(newGet().setURI(uri), PubSubValidator.validateContentFormat(contentFormat)));
    }

    @Override
    public void read(CoapHandler handler, int contentFormat) {
        asynchronous(format(newGet().setURI(uri), 
                PubSubValidator.validateContentFormat(contentFormat)), handler);
    }

    @Override
    public CoapResponse remove() {
        return synchronous(newDelete().setURI(uri));
    }

    @Override
    public void remove(CoapHandler handler) {
        asynchronous(newDelete().setURI(uri), handler);
    }

    /**
     * Stores token in map to ensure that only one subscription per topic (URI) per client.
     * @param request
     * @param handler
     * @return the relation
     */
    private CoapPubSubObserveRelation handleSubscribe(Request request, CoapHandler handler) {
        if (!subscribeToken.containsKey(this.getURI())) {
            CoapPubSubObserveRelation relation = observeAndWait(request, handler);
            if (relation != null) {
                subscribeToken.put(this.getURI(), relation.getCurrent().advanced().getToken());
                LOGGER.log(Level.FINE, "Subscribe, URI {0} is subscribed and token {1} stored", new Object[]{
                    this.getURI(), relation.getCurrent().advanced().getToken()});
            } else {
                if (subscribeToken.contains(this.getURI())) {
                    subscribeToken.remove(this.getURI());
                    LOGGER.log(Level.FINE, "Subscribe, URI {0} removed subscriber in map", this.getURI());
                }
            }
            return relation;
        } else {
            LOGGER.log(Level.FINE, "Subscribe, URI {0} is has already subscribed", this.getURI());
            return null;
        }
    }

    /**
     * Sends unsubscribe request with correct token for this topic (URI)
     * @return the response
     */
    private CoapResponse handleUnsubscribe() {
        if (subscribeToken.containsKey(this.getURI())) {
            byte[] token = subscribeToken.get(this.getURI());
            Request request = newGet().setURI(uri).setObserveCancel();
            request.setToken(token);
            CoapResponse response = synchronous(request);
            if (response != null) {
                if (subscribeToken.containsKey(this.getURI())) {
                    subscribeToken.remove(this.getURI());
                    LOGGER.log(Level.FINE, "Unsubscribe, URI {0} removed from storage", this.getURI());
                } else {
                    LOGGER.log(Level.FINE, "Unsubscribe, URI {0} is not in the subscribed tokens map", this.getURI());
                }
            }
            return response;
        } else {
            LOGGER.log(Level.FINE, "Unsubscribe, no token is set for this URI {0}", this.getURI());
            return null;
        }
    }
    
    // ETag validation
    public CoapResponse validate(byte[]... etags) {
        return synchronous(etags(newGet().setURI(uri), etags));
    }

    public void validate(CoapHandler handler, byte[]... etags) {
        asynchronous(etags(newGet().setURI(uri), etags), handler);
    }

    // Advanced requests
    /**
     * Sends an advanced synchronous request that has to be configured by the
     * developer.
     *
     * @param request the custom request
     * @return the CoAP response
     */
    public CoapResponse advanced(Request request) {
        assignClientUriIfEmpty(request);
        return synchronous(request);
    }

    /**
     * Sends an advanced asynchronous request that has to be configured by the
     * developer.
     *
     * @param handler the response handler
     * @param request the custom request
     */
    public void advanced(CoapHandler handler, Request request) {
        assignClientUriIfEmpty(request);
        asynchronous(request, handler);
    }

    /**
     * Stops the client-specific executor service to cleanly exit programs. Only
     * needed if {@link #useExecutor()} or {@link #setExecutor(ExecutorService)}
     * are used (i.e., a client-specific executor service was set).
     */
    public void shutdown() {
        if (this.executor != null) {
            this.executor.shutdownNow();
        }
    }

    // Implementation
    /*
	 * Asynchronously sends the specified request and invokes the specified
	 * handler when a response arrives.
	 *
	 * @param request the request
	 * @param handler the Response handler
     */
    private void asynchronous(Request request, CoapHandler handler) {
        request.addMessageObserver(new MessageObserverImpl(handler));
        send(request);
    }

    /*
	 * Synchronously sends the specified request.
	 *
	 * @param request the request
	 * @return the CoAP response
     */
    private CoapResponse synchronous(Request request) {
        return synchronous(request, getEffectiveEndpoint(request));
    }

    /*
	 * Synchronously sends the specified request over the specified endpoint.
	 *
	 * @param request the request
	 * @param endpoint the endpoint
	 * @return the CoAP response
     */
    private CoapResponse synchronous(Request request, Endpoint outEndpoint) {
        try {
            Response response = send(request, outEndpoint).waitForResponse(getTimeout());
            if (response == null) {
                // Cancel request so appropriate clean up can happen.
                request.cancel();
                return null;
            } else {
                return new CoapResponse(response);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /*
	 * Sets the specified Content-Format to the specified request.
	 *
	 * @param request the request
	 * @param contentFormat the Content-Format
	 * @return the request
     */
    private static Request format(final Request request, final int contentFormat) {
        request.getOptions().setContentFormat(contentFormat);
        return request;
    }

    /*
	 * Sets the specified Accept option of the request.
	 *
	 * @param request the request
	 * @param accept the Accept option
	 * @return the request
     */
    private static Request accept(final Request request, final int accept) {
        request.getOptions().setAccept(accept);
        return request;
    }

    /*
	 * Adds the specified ETag options to the request.
	 * 
	 * @param request the request
	 * @param etags the list of ETags
	 * @return the request
     */
    private static Request etags(final Request request, final byte[]... etags) {
        for (byte[] etag : etags) {
            request.getOptions().addETag(etag);
        }
        return request;
    }

    /*
	 * Adds the specified ETags as If-Match options to the request.
	 * 
	 * @param request the request
	 * @param etags the ETags for the If-Match option
	 * @return the request
     */
    private static Request ifMatch(final Request request, final byte[]... etags) {
        for (byte[] etag : etags) {
            request.getOptions().addIfMatch(etag);
        }
        return request;
    }

    /*
	 * Adds the specified ETags as If-Match options to the request.
	 * 
	 * @param request the request
	 * @param etags the ETags for the If-Match option
	 * @return the request
     */
    private static Request ifNoneMatch(final Request request) {
        request.getOptions().setIfNoneMatch(true);
        return request;
    }

    /**
     * Sends the specified observe request and invokes the specified handler
     * each time a notification arrives.
     *
     * @param request the request
     *
     * @param handler the Response handler
     *
     * @return the CoAP observe relation
     * @throws IllegalArgumentException if the observe option is not set in the
     * request
     */
    public CoapPubSubObserveRelation observe(Request request, CoapHandler handler) {
        if (request.getOptions().hasObserve()) {
            Endpoint outEndpoint = getEffectiveEndpoint(request);
            CoapPubSubObserveRelation relation = new CoapPubSubObserveRelation(request, outEndpoint);
            request.addMessageObserver(new ObserveMessageObserverImpl(handler, relation));
            send(request, outEndpoint);
            return relation;
        } else {
            throw new IllegalArgumentException("please make sure that the request has observe option set.");
        }
    }

    /**
     * Sends the specified observe request and waits for the reply to further
     * invokes the specified handler each time a notification arrives.
     *
     * @param request the request
     *
     * @param handler the Response handler
     *
     * @return the CoAP observe relation
     * @throws IllegalArgumentException if the observe option is not set in the
     * request
     */
    protected CoapPubSubObserveRelation observeAndWait(Request request, CoapHandler handler) {
        if (request.getOptions().hasObserve()) {
            try {
                Endpoint outEndpoint = getEffectiveEndpoint(request);
                CoapPubSubObserveRelation relation = new CoapPubSubObserveRelation(request, outEndpoint);
                request.addMessageObserver(new ObserveMessageObserverImpl(handler, relation));
                Response response = send(request, outEndpoint).waitForResponse(getTimeout());
                CoapResponse coapResponse = new CoapResponse(response);
                relation.onResponse(coapResponse);
                return relation;
            } catch (InterruptedException e) {
                System.err.println("Wait interrupted" + e);
                return null;
            }
        } else {
            throw new IllegalArgumentException("please make sure that the request has observe option set.");
        }
    }

    /**
     * Sends the specified request over the endpoint of the client if one is
     * defined or over the default endpoint otherwise.
     *
     * @param request the request
     * @return the request
     */
    protected Request send(Request request) {
        return send(request, getEffectiveEndpoint(request));
    }

    /**
     * Sends the specified request over the specified endpoint.
     *
     * @param request the request
     * @param outEndpoint the endpoint
     * @return the request
     */
    protected Request send(Request request, Endpoint outEndpoint) {
        if (blockwise != 0) {
            request.getOptions().setBlock2(new BlockOption(BlockOption.size2Szx(this.blockwise), false, 0));
        }

        outEndpoint.sendRequest(request);
        return request;
    }

    /**
     * Returns the effective endpoint that the specified request is supposed to
     * be sent over. If an endpoint has explicitly been set to this CoapClient,
     * this endpoint will be used. If no endpoint has been set, the client will
     * effectively use a default endpoint of the {@link EndpointManager}.
     *
     * @param request the request to be sent
     * @return the effective endpoint that the request is going o be sent over.
     */
    protected Endpoint getEffectiveEndpoint(Request request) {
        Endpoint myEndpoint = getEndpoint();

        // custom endpoint
        if (myEndpoint != null) {
            return myEndpoint;
        }

        // default endpoints
        if (CoAP.COAP_SECURE_URI_SCHEME.equals(request.getScheme())) {
            // this is the case when secure coap is supposed to be used
            return EndpointManager.getEndpointManager().getDefaultSecureEndpoint();
        } else if (CoAP.COAP_TCP_URI_SCHEME.equals(request.getScheme())) {
            // Running over TCP.
            return EndpointManager.getEndpointManager().getDefaultTcpEndpoint();
        } else if (CoAP.COAP_SECURE_TCP_URI_SCHEME.equals(request.getScheme())) {
            // Running over TLS.
            return EndpointManager.getEndpointManager().getDefaultSecureTcpEndpoint();
        } else {
            // this is the normal case
            return EndpointManager.getEndpointManager().getDefaultEndpoint();
        }
    }

    /*
	 * Create a GET request with a type specified in the client
	 *
	 * @return the request
     */
    private Request newGet() {
        return applyRequestType(Request.newGet());
    }

    /*
	 * Create a POST request with a type specified in the client
	 *
	 * @return the request
     */
    private Request newPost() {
        return applyRequestType(Request.newPost());
    }

    /*
	 * Create a PUT request with a type specified in the client
	 *
	 * @return the request
     */
    private Request newPut() {
        return applyRequestType(Request.newPut());
    }

    /*
	 * Create a DELETE request with a type specified in the client
	 *
	 * @return the request
     */
    private Request newDelete() {
        return applyRequestType(Request.newDelete());
    }

    /*
	 * Applies the CoapClient#type (managed by useNONs, useCONs) to the specified request.
	 *
	 *
	 * @param request the request
	 * @return the same request with changed type
     */
    private Request applyRequestType(Request request) {
        request.setType(this.type);
        return request;
    }

    /*
	 * Assigns a CoapClient#uri if request has no uri.
	 *
	 * @param request the request
     */
    private void assignClientUriIfEmpty(Request request) {
        // request.getUri() is a computed getter and never returns null so checking destination
        if (request.getDestination() == null) {
            request.setURI(uri);
        }
    }

    /**
     * The MessageObserverImpl is called when a response arrives. It wraps the
     * response into a CoapResponse and lets the executor invoke the handler's
     * method.
     */
    private class MessageObserverImpl extends MessageObserverAdapter {

        /**
         * The handler.
         */
        protected CoapHandler handler;

        /**
         * Constructs a new message observer that calls the specified handler
         *
         * @param handler the Response handler
         */
        private MessageObserverImpl(CoapHandler handler) {
            this.handler = handler;
        }

        /* (non-Javadoc)
		 * @see org.eclipse.californium.core.coap.MessageObserverAdapter#responded(org.eclipse.californium.core.coap.Response)
         */
        @Override
        public void onResponse(final Response response) {
            succeeded(response != null ? new CoapResponse(response) : null);
        }

        /* (non-Javadoc)
		 * @see org.eclipse.californium.core.coap.MessageObserverAdapter#rejected()
         */
        @Override
        public void onReject() {
            failed();
        }

        /* (non-Javadoc)
		 * @see org.eclipse.californium.core.coap.MessageObserverAdapter#timedOut()
         */
        @Override
        public void onTimeout() {
            failed();
        }

        /**
         * Invoked when a response arrives (even if the response code is not
         * successful, the response still was successfully transmitted).
         *
         * @param response the response
         */
        protected void succeeded(final CoapResponse response) {
            // use thread from the protocol stage
            if (executor == null) {
                deliver(response);
            } // use thread from the client executer
            else {
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            deliver(response);
                        } catch (Throwable t) {
                            LOGGER.log(Level.WARNING, "Exception while handling response", t);
                        }
                    }
                });
            }
        }

        /**
         * Invokes the handler's method with the specified response. This method
         * must be invoked by the client's executor if it defines one.
         *
         * This is a separate method, so that other message observers can add
         * synchronization code, e.g., for CoAP notification re-ordering.
         *
         * @param response the response
         */
        protected void deliver(CoapResponse response) {
            handler.onLoad(response);
        }

        /**
         * Invokes the handler's method failed() on the executor.
         */
        protected void failed() {
            // use thread from the protocol stage
            if (executor == null) {
                handler.onError();
            } // use thread from the client executer
            else {
                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            handler.onError();
                        } catch (Throwable t) {
                            LOGGER.log(Level.WARNING, "Exception while handling failure", t);
                        }
                    }
                });
            }
        }
    }

    /**
     * The ObserveMessageObserverImpl is called whenever a notification of an
     * observed resource arrives. It wraps the response into a CoapResponse and
     * lets the executor invoke the handler's method.
     */
    private class ObserveMessageObserverImpl extends MessageObserverImpl {

        /**
         * The observer relation relation.
         */
        private final CoapPubSubObserveRelation relation;

        /**
         * Constructs a new message observer with the specified handler and the
         * specified relation.
         *
         * @param handler the Response handler
         * @param relation the Observe relation
         */
        public ObserveMessageObserverImpl(CoapHandler handler, CoapPubSubObserveRelation relation) {
            super(handler);
            this.relation = relation;
        }

        /**
         * Checks if the specified response truly is a new notification and if,
         * invokes the handler's method or drops the notification otherwise.
         * Ordering and delivery must be done synchronized here to deal with
         * race conditions in the stack.
         */
        @Override
        protected void deliver(CoapResponse response) {
            synchronized (relation) {
                if (relation.onResponse(response)) {
                    handler.onLoad(response);
                } else {
                    LOGGER.log(Level.FINER, "Dropping old notification: {0}", response.advanced());
                    return;
                }
            }
        }

        /**
         * Marks the relation as canceled and invokes the the handler's failed()
         * method.
         */
        @Override
        protected void failed() {
            relation.setCanceled(true);
            super.failed();
        }
    }

    /**
     * The Builder can be used to build a CoapClient if the URI's pieces are
     * available in separate strings. This is in particular useful to add
     * multiple queries to the URI.
     */
    public static class Builder {

        /**
         * The scheme, host and port.
         */
        String scheme, host, port;

        /**
         * The path and the query.
         */
        String[] path, query;

        /**
         * Instantiates a new builder.
         *
         * @param host the host
         * @param port the port
         */
        public Builder(String host, int port) {
            this.host = host;
            this.port = Integer.toString(port);
        }

        /**
         * Sets the specified scheme.
         *
         * @param scheme the scheme
         * @return the builder
         */
        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        /**
         * Sets the specified host.
         *
         * @param host the host
         * @return the builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the specified port.
         *
         * @param port the port
         * @return the builder
         */
        public Builder port(String port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the specified port.
         *
         * @param port the port
         * @return the builder
         */
        public Builder port(int port) {
            this.port = Integer.toString(port);
            return this;
        }

        /**
         * Sets the specified resource path.
         *
         * @param path the path
         * @return the builder
         */
        public Builder path(String... path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the specified query.
         *
         * @param query the query
         * @return the builder
         */
        public Builder query(String... query) {
            this.query = query;
            return this;
        }

        /**
         * Creates the CoapClient
         *
         * @return the client
         */
        public CoapClient create() {
            StringBuilder builder = new StringBuilder();
            if (scheme != null) {
                builder.append(scheme).append("://");
            }
            builder.append(host).append(":").append(port);
            for (String element : path) {
                builder.append("/").append(element);
            }
            if (query.length > 0) {
                builder.append("?");
            }
            for (int i = 0; i < query.length; i++) {
                builder.append(query[i]);
                if (i < query.length - 1) {
                    builder.append("&");
                }
            }
            return new CoapClient(builder.toString());
        }
    }

}
