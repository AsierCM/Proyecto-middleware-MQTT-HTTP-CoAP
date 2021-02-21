/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.eclipse.californium.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.BrokerInterface;
import org.eclipse.californium.core.server.BrokerMessageDeliverer;
import org.eclipse.californium.core.server.MessageDeliverer;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.DiscoveryResource;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.util.NamedThreadFactory;

import org.eclipse.californium.core.PubSubTopic;
import com.proyecto.ProtocoloInteraccionCoap;

/**
 * The PubSub broker acting very similar to a CoAPserver in Californium's
 * implementation. The root resource is set to '/ps/' to ensure that the PubSub
 * clients operate via this URI.
 *
 * @author Thomas Wiss
 */
public class CoapBroker implements BrokerInterface {

    /**
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(CoapServer.class.getName());

    /**
     * The root resource.
     */
    private final Resource root;

    /**
     * The root resource for pubsub API (/ps/)
     */
    private final Resource ps;

    private final NetworkConfig config;

    /**
     * The message deliverer.
     */
    private MessageDeliverer deliverer;

    /**
     * The list of endpoints the server connects to the network.
     */
    private final List<Endpoint> endpoints;

    /**
     * The executor of the server for its endpoints (can be null).
     */
    private ScheduledExecutorService executor;

    private boolean running;

    /**
     * Constructs a default server. The server starts after the method
     * {@link #start()} is called. If a server starts and has no specific ports
     * assigned, it will bind to CoAp's default port 5683.
     */
    public CoapBroker() {
        this(NetworkConfig.getStandard());
    }

    /**
     * Constructs a server that listens to the specified port(s) after method
     * {@link #start()} is called.
     *
     * @param ports the ports to bind to
     */
    public CoapBroker(final int... ports) {
        this(NetworkConfig.getStandard(), ports);
    }

    /**
     * Constructs a server with the specified configuration that listens to the
     * specified ports after method {@link #start()} is called.
     *
     * @param config the configuration, if <code>null</code> the configuration
     * returned by {@link NetworkConfig#getStandard()} is used.
     * @param ports the ports to bind to
     */
    public CoapBroker(final NetworkConfig config, final int... ports) {

        // global configuration that is passed down (can be observed for changes)
        if (config != null) {
            this.config = config;
        } else {
            this.config = NetworkConfig.getStandard();
        }

        // resources
        this.root = createRoot();

        CoapResource wellKnown = new CoapResource(".well-known");
        wellKnown.setVisible(false);
        wellKnown.add(new DiscoveryResource(root));
        ps = new PubSubRootResource(root);
        root.add(wellKnown);
        root.add(ps);

        this.deliverer = new BrokerMessageDeliverer(root, ps);

        // endpoints
        this.endpoints = new ArrayList<>();
        // sets the central thread pool for the protocol stage over all endpoints
        this.executor = Executors.newScheduledThreadPool(//
                this.config.getInt(NetworkConfig.Keys.PROTOCOL_STAGE_THREAD_COUNT), //
                new NamedThreadFactory("CoapBroker#")); //$NON-NLS-1$
        // create endpoint for each port
        for (int port : ports) {
            addEndpoint(new CoapEndpoint(port, this.config));
        }

    }

    /**
     * Sets the executor service to use for running tasks in the protocol stage.
     *
     * @param executor The thread pool to use.
     * @throws IllegalStateException if this server is running.
     */
    public synchronized void setExecutor(final ScheduledExecutorService executor) {

        if (running) {
            throw new IllegalStateException("executor service can not be set on running server");
        } else {
            this.executor = executor;
            for (Endpoint ep : endpoints) {
                ep.setExecutor(executor);
            }
        }
    }

    /**
     * Starts the server by starting all endpoints this server is assigned to.
     * Each endpoint binds to its port. If no endpoint is assigned to the
     * server, an endpoint is started on the port defined in the config.
     */
    @Override
    public synchronized void start() {

        if (running) {
            return;
        }

        LOGGER.info("Starting broker");

        if (endpoints.isEmpty()) {
            // servers should bind to the configured port (while clients should use an ephemeral port through the default endpoint)
            int port = config.getInt(NetworkConfig.Keys.COAP_PORT);
            LOGGER.log(Level.INFO, "No endpoints have been defined for broker, setting up server endpoint on default port {0}", port);
            addEndpoint(new CoapEndpoint(port, this.config));
        }

        int started = 0;
        for (Endpoint ep : endpoints) {
            try {
                ep.start();
                // only reached on success
                ++started;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Cannot start broker endpoint [" + ep.getAddress() + "]", e);
            }
        }
        if (started == 0) {
            throw new IllegalStateException("None of the broker endpoints could be started");
        } else {
            running = true;
        }
    }

    /**
     * Stops the server, i.e., unbinds it from all ports. Frees as much system
     * resources as possible to still be able to be re-started with the previous
     * binds.
     */
    @Override
    public synchronized void stop() {

        if (running) {
            LOGGER.info("Stopping broker");
            for (Endpoint ep : endpoints) {
                ep.stop();
            }
            running = false;
        }
    }

    /**
     * Destroys the server, i.e., unbinds from all ports and frees all system
     * resources.
     */
    @Override
    public synchronized void destroy() {

        LOGGER.info("Destroying broker");
        // prevent new tasks from being submitted
        executor.shutdown(); // cannot be started again
        try {
            // wait for currently executing tasks to complete
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                // cancel still executing tasks
                // and ignore all remaining tasks scheduled for later
                List<Runnable> runningTasks = executor.shutdownNow();
                if (runningTasks.size() > 0) {
                    // this is e.g. the case if we have performed an incomplete blockwise transfer
                    // and the BlockwiseLayer has scheduled a pending BlockCleanupTask for tidying up
                    LOGGER.log(Level.FINE, "Ignoring remaining {0} scheduled task(s)", runningTasks.size());
                }
                // wait for executing tasks to respond to being cancelled
                executor.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            for (Endpoint ep : endpoints) {
                ep.destroy();
            }
            LOGGER.log(Level.INFO, "CoAP broker has been destroyed");
            running = false;
        }
    }

    @Override
    public BrokerInterface addTopic(Resource... resources) {
        for (Resource r : resources) {
            ps.add(r);
        }
        return this;
    }

    @Override
    public boolean removeTopic(Resource resource) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Sets the message deliverer.
     *
     * @param deliverer the new message deliverer
     */
    public void setMessageDeliverer(final MessageDeliverer deliverer) {
        this.deliverer = deliverer;
        for (Endpoint endpoint : endpoints) {
            endpoint.setMessageDeliverer(deliverer);
        }
    }

    /**
     * Gets the message deliverer.
     *
     * @return the message deliverer
     */
    public MessageDeliverer getMessageDeliverer() {
        return deliverer;
    }

    /**
     * Adds an Endpoint to the server. WARNING: It automatically configures the
     * default executor of the server. Endpoints that should use their own
     * executor (e.g., to prioritize or balance request handling) either set it
     * afterwards before starting the server or override the setExecutor()
     * method of the special Endpoint.
     *
     * @param endpoint the endpoint to add
     */
    @Override
    public void addEndpoint(final Endpoint endpoint) {
        endpoint.setMessageDeliverer(deliverer);
        endpoint.setExecutor(executor);
        endpoints.add(endpoint);
    }

    /**
     * Gets the list of endpoints this server is connected to.
     *
     * @return the endpoints
     */
    @Override
    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    /**
     * Returns the endpoint with a specific port.
     *
     * @param port the port
     * @return the endpoint
     */
    @Override
    public Endpoint getEndpoint(int port) {
        Endpoint endpoint = null;

        for (Endpoint ep : endpoints) {
            if (ep.getAddress().getPort() == port) {
                endpoint = ep;
            }
        }
        return endpoint;
    }

    /**
     * Returns the endpoint with a specific socket address.
     *
     * @param address the socket address
     * @return the endpoint
     */
    @Override
    public Endpoint getEndpoint(InetSocketAddress address) {
        Endpoint endpoint = null;

        for (Endpoint ep : endpoints) {
            if (ep.getAddress().equals(address)) {
                endpoint = ep;
                break;
            }
        }

        return endpoint;
    }

    /**
     * Gets the root of this server.
     *
     * @return the root
     */
    public Resource getRoot() {
        return root;
    }

    public Resource getCore() {
        return root;
    }


    /**
     * Creates a root for this server. Can be overridden to create another root.
     *
     * @return the resource
     */
    protected Resource createRoot() {
        return new CoapBroker.RootResource();
    }

    public void setProtocoloInteraccionCoapSuper(ProtocoloInteraccionCoap _protocoloInteraccionCoap){
	((PubSubTopic)ps).setProtocoloInteraccionCoap(_protocoloInteraccionCoap);
	((BrokerMessageDeliverer)deliverer).setProtocoloInteraccionCoap(_protocoloInteraccionCoap);
    }
    /**
     * Represents the root of a resource tree.
     */
    private class RootResource extends CoapResource {

        // get version from Maven package
        private static final String SPACE = "                                               "; // 47 until line end
        private final String VERSION = CoapServer.class.getPackage().getImplementationVersion() != null
                ? "Cf " + CoapServer.class.getPackage().getImplementationVersion() : SPACE;
        private final String msg = new StringBuilder()
                .append("************************************************************\n")
                .append("CoAP RFC 7252").append(SPACE.substring(VERSION.length())).append(VERSION).append("\n")
                .append("************************************************************\n")
                .append("This server is using the Eclipse Californium (Cf) CoAP framework\n")
                .append("published under EPL+EDL: http://www.eclipse.org/californium/\n")
                .append("\n")
                .append("(c) 2014, 2015, 2016 Institute for Pervasive Computing, ETH Zurich and others\n")
                .append("************************************************************")
                .toString();

        public RootResource() {
            super("");
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            exchange.respond(CoAP.ResponseCode.CONTENT, msg);
        }

        public List<Endpoint> getEndpoints() {
            return CoapBroker.this.getEndpoints();
        }
    }

    private class GarabageCollectionExecutor extends TimerTask {

        @Override
        public void run() {
            Collection<Resource> children = ps.getChildren();
            if (!children.isEmpty()) {
                Iterator iter = children.iterator();
                if (iter.hasNext()) {
                    PubSubTopic child = (PubSubTopic) iter.next();
                    child.conductBasicTopicGarbageCollection();
                }
            }

        }
    }
}
