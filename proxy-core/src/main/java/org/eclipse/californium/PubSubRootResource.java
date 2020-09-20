/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.eclipse.californium.core;

import java.util.List;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;

/**
 * Represents the root resource for the PubSub API and client operations.
 * @author Thomas Wiss
 */
public class PubSubRootResource extends PubSubTopic {

    /**
     * The Constant PS.
     */
    public static final String PS = "ps";

    /**
     * The root of the server's resource tree
     */
    private final Resource root;

    /**
     * Instantiates a new discovery resource.
     *
     * @param root the root resource of the server
     */
    public PubSubRootResource(Resource root) {
        this(PS, root);
    }

    /**
     * Instantiates a new discovery resource with the specified name.
     *
     * @param name the name
     * @param root the root resource of the server
     */
    public PubSubRootResource(String name, Resource root) {
        super(name, MediaTypeRegistry.APPLICATION_LINK_FORMAT, 0);
        this.root = root;
        setObservable(false);
        getAttributes().addContentType(MediaTypeRegistry.APPLICATION_LINK_FORMAT);
        getAttributes().addResourceType("core.ps");  // mandatory according to section 4,1 in https://tools.ietf.org/html/draft-ietf-core-coap-pubsub-01
        getAttributes().addResourceType("core.ps.discover");  // mandatory according to section 4,1 in https://tools.ietf.org/html/draft-ietf-core-coap-pubsub-01
    }

    /**
     * Responds with a list of all resources of the server, i.e. links.
     *
     * @param exchange the exchange
     */
    @Override
    public void handleGET(CoapExchange exchange) {
        String tree = discoverTree(root, exchange.getRequestOptions().getUriQuery());
        exchange.respond(CoAP.ResponseCode.CONTENT, tree, MediaTypeRegistry.APPLICATION_LINK_FORMAT);
        conductBasicTopicGarbageCollection();
    }

    /**
     * Allows a client to create or add a topic to the /ps/ API via PUT message
     *
     * @param exchange the exchange
     */
    @Override
    public void handlePUT(CoapExchange exchange) {
        // Auth, otherwise 4.01
        handlePost(exchange, true);
    }

    /**
     * Allows a client to create or add a topic to the /ps/ API
     *
     * @param exchange the exchange
     */
    @Override
    public void handlePOST(CoapExchange exchange) {
        // Auth, otherwise 4.01
        handlePost(exchange, true);
    }

    /**
     * To delete the PS-API root is not allowed.
     *
     * @param exchange the exchange
     */
    @Override
    public void handleDELETE(CoapExchange exchange) {
        exchange.respond(CoAP.ResponseCode.METHOD_NOT_ALLOWED);
    }

    /**
     * Builds up the list of resources of the specified root resource. Queries
     * serve as filter and might prevent undesired resources from appearing on
     * the list.
     *
     * @param root the root resource of the server
     * @param queries the queries
     * @return the list of resources as string
     */
    public String discoverTree(Resource root, List<String> queries) {
        StringBuilder buffer = new StringBuilder();
        for (Resource child : root.getChildren()) {
            LinkFormat.serializeTree(child, queries, buffer);
        }

        // remove last comma ',' of the buffer
        if (buffer.length() > 1) {
            buffer.delete(buffer.length() - 1, buffer.length());
        }

        return buffer.toString();
    }
}
