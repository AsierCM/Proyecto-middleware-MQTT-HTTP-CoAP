package org.eclipse.californium.core.server;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.californium.core.PubSubValidator;
import org.eclipse.californium.core.coap.CoAP;

import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveManager;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.observe.ObservingEndpoint;
import org.eclipse.californium.core.server.resources.Resource;

import com.proyecto.ProtocoloInteraccionCoap;
import org.eclipse.californium.core.PubSubTopic;

/**
 * The BrokerMessageDeliverer delivers requests to corresponding resources and
 * responses to corresponding requests.
 * Work based on Californium's implementation of the ServerMessageDeliverer.
 * 
 * @author Thomas Wiss
 */
public final class BrokerMessageDeliverer implements MessageDeliverer {

    private static final Logger LOGGER = Logger.getLogger(BrokerMessageDeliverer.class.getCanonicalName());

    /* The root of all resources */
    private final Resource root;
    private final Resource ps;

    /* The manager of the observe mechanism for this server */
    private final ObserveManager observeManager = new ObserveManager();

    public static ProtocoloInteraccionCoap protocoloInteraccionCoap;

    /**
     * Constructs a default message deliverer that delivers requests to the
     * resources rooted at the specified root.
     *
     * @param root the root resource
     * @param ps the pubsubAPI resource
     */
    public BrokerMessageDeliverer(final Resource root, final Resource ps) {
        this.root = root;
        this.ps = ps;
    }

    /* (non-Javadoc)
	 * @see org.eclipse.californium.MessageDeliverer#deliverRequest(org.eclipse.californium.network.Exchange)
     */
    @Override
    public void deliverRequest(final Exchange exchange) {
        Request request = exchange.getRequest();
        List<String> path = request.getOptions().getUriPath();
        String originalURIPath = request.getOptions().getUriPathString();
        final Resource resource = findResource(path);
        if (resource != null) { //entra si el recurso existe entre los recursos /ps/...
	

	    Boolean seguir = true;
	    //Primero compruebo si es un recurso de un server externo y actualizo su informacion

	    if(request.getCode() == CoAP.Code.GET){
	        seguir = protocoloInteraccionCoap.iniciarComunicacion(null, null, null, exchange, null, null);
	    }
	
	    if(seguir){
		    checkForObserveOption(exchange, resource);

		    // Get the executor and let it process the request
		    Executor executor = resource.getExecutor();
		    if (executor != null) {
		        exchange.setCustomExecutor();
		        executor.execute(new Runnable() {
		            @Override
		            public void run() {
		                resource.handleRequest(exchange);
		            }
		        });
		    } else {
		        resource.handleRequest(exchange);
		    }
	     }
        } else if (request.getCode() == CoAP.Code.PUT && PubSubValidator.uriStartsWithPS(originalURIPath)) { // allows creating new topics with PUT on /ps/
            ps.handleRequest(exchange);
        } else {
            LOGGER.log(Level.INFO, "Did not find resource {0} requested by {1}:{2}",
                    new Object[]{path, request.getSource(), request.getSourcePort()});
            exchange.sendResponse(new Response(ResponseCode.NOT_FOUND));
        }
    }

    /**
     * Checks whether an observe relationship has to be established or canceled.
     * This is done here to have a server-global observeManager that holds the
     * set of remote endpoints for all resources. This global knowledge is
     * required for efficient orphan handling.
     *
     * @param exchange the exchange of the current request
     * @param resource the target resource
     * @param path the path to the resource
     */
    private void checkForObserveOption(final Exchange exchange, final Resource resource) {
        Request request = exchange.getRequest();
        if (request.getCode() != Code.GET) {
            return;
        }

        InetSocketAddress source = new InetSocketAddress(request.getSource(), request.getSourcePort());

        if (request.getOptions().hasObserve() && resource.isObservable()) {

            if (request.getOptions().getObserve() == 0) {
                // Requests wants to observe and resource allows it :-)
                LOGGER.log(Level.FINE,
                        "Initiate an observe relation between {0}:{1} and resource {2}",
                        new Object[]{request.getSource(), request.getSourcePort(), resource.getURI()});
                ObservingEndpoint remote = observeManager.findObservingEndpoint(source);
                ObserveRelation relation = new ObserveRelation(remote, resource, exchange);
                remote.addObserveRelation(relation);
                exchange.setRelation(relation);

                // all that's left is to add the relation to the resource which
                // the resource must do itself if the response is successful
            } else if (request.getOptions().getObserve() == 1) {
                // Observe defines 1 for canceling
                LOGGER.log(Level.FINE,
                        "End (Observer == 1) an observe relation between {0}:{1} and resource {2}",
                        new Object[]{request.getSource(), request.getSourcePort(), resource.getURI()});
                ObserveRelation relation = observeManager.getRelation(source, request.getToken());
                if (relation != null) {
                    LOGGER.log(Level.FINE,"Canceling relation");
                    relation.cancel();
                }
            }
        }
    }

    /**
     * Searches in the resource tree for the specified path. A parent resource
     * may accept requests to subresources, e.g., to allow addresses with
     * wildcards like <code>coap://example.com:5683/devices/*</code>
     *
     * @param list the path as list of resource names
     * @return the resource or null if not found
     */
    private Resource findResource(final List<String> list) {
        LinkedList<String> path = new LinkedList<>(list);
        Resource current = root;
        while (!path.isEmpty() && current != null) {
            String name = path.removeFirst();
            current = current.getChild(name);
        }
        return current;
    }

    /* (non-Javadoc)
	 * @see org.eclipse.californium.MessageDeliverer#deliverResponse(org.eclipse.californium.network.Exchange, org.eclipse.californium.coap.Response)
     */
    @Override
    public void deliverResponse(Exchange exchange, Response response) {
        if (response == null) {
            throw new NullPointerException("Response must not be null");
        } else if (exchange == null) {
            throw new NullPointerException("Exchange must not be null");
        } else if (exchange.getRequest() == null) {
            throw new IllegalArgumentException("Exchange does not contain request");
        } else {
            exchange.getRequest().setResponse(response);
        }
    }
    public void setProtocoloInteraccionCoap(ProtocoloInteraccionCoap _protocoloInteraccionCoap){
	protocoloInteraccionCoap = _protocoloInteraccionCoap;
    }
}
