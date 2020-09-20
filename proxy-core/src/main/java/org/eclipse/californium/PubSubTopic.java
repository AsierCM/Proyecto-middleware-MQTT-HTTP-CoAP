/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.eclipse.californium.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;

import com.proyecto.ProtocoloInteraccionCoap;


/**
 * Represents a topic for a CoAP PubSub Client to perform all operations
 * specified in the PubSubAPI.
 *
 * @author Thomas Wiss
 */
public class PubSubTopic extends CoapResource {

    private static final Logger LOGGER = Logger.getLogger(PubSubTopic.class.getCanonicalName());
    
    private static final int TIMEOUT_MSEC_TOO_MANY_REQUESTS = 100; // Timeout in milliseconds
    
    private static final int TIMEOUT_RETRY_SEC_TOO_MANY_REQUESTS = 11; // Timeout in seconds

    private static final String PUBSUB_API_URI = "/ps/";

    private int topicContentFormat = -1;

    private TopicSubject subject = null;

    private Semaphore semaphore = new Semaphore(1);

    public static ProtocoloInteraccionCoap protocoloInteraccionCoap;

    public PubSubTopic(String name, int contentType, long maxAgeTopic) {
        super(name);
        setObservable(true);
        setVisible(true);
        getAttributes().addResourceType(name);
        getAttributes().setObservable();
        setContentType(contentType);
        subject = new TopicSubject();
        subject.setMaxAgeOfValue(maxAgeTopic);
        subject.updateTopicSubjectTTL();

	this.setObserveType(Type.CON);
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        handleGet(exchange);
        subject.updateTopicSubjectTTL();
    }

    @Override
    public void handlePOST(CoapExchange exchange) {
        handlePost(exchange, false);
        subject.updateTopicSubjectTTL();
    }

    @Override
    public void handlePUT(CoapExchange exchange) {
        handlePut(exchange);
        subject.updateTopicSubjectTTL();
    }

    @Override
    public void handleDELETE(CoapExchange exchange) {
        this.conductBasicTopicGarbageCollection();
        handleDelete(exchange);
    }

    //
    //
    // ------- Handle incoming data --------- //
    //
    //
    /**
     * Sub-Method to handle an incoming GET exchange
     *
     * @param exchange
     */
    protected void handleGet(CoapExchange exchange) {
        String exchangeURI = "/" + exchange.getRequestOptions().getUriPathString();
        // Auth, otherwise 4.01

        if (this.getURI().equals(exchangeURI)) {
            try {
                if (!exchange.getRequestOptions().getUriQuery().isEmpty()) {
                    //
                    // For discovery only!
                    // should be directed to the ps-resource or well-knwon core only... so all in here are invalid
                    //
                    LOGGER.log(Level.INFO, "handleGET: Found query in a topic, not supported");
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
                } else if (exchange.getRequestOptions().hasObserve()) {
                    if (exchange.advanced().getRequest().getToken().length > 1) {
                        LOGGER.log(Level.FINE, "handleGET: request with observe and a token");
                        handleObserve(exchange);
                    } else {
                        LOGGER.log(Level.FINE, "handleGET: No token in request, invalid");
                        System.err.println("Sub or UnSub request has no token");
                    }

                } else {
                    handleRead(exchange);
                }
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "General failure occured. Text: {0}", e);
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
                System.err.println(e);
            }
        } else {
            LOGGER.log(Level.INFO, "handleGET: Wrong topic, differing uri between request and topic");
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
        }
    }

    /**
     * Sub-Method to handle an incoming PUT exchange
     *
     * @param exchange
     */
    protected void handlePut(CoapExchange exchange) {
        String exchangeURI = "/" + exchange.getRequestOptions().getUriPathString();
	//exchangeURI es del tipo "/ps/topic/subtopic..."
        // Auth, otherwise 4.01
	System.out.println("LLEGO AL PUT");
        try {
            if (this.getContentType() != exchange.getRequestOptions().getContentFormat()) {
                LOGGER.log(Level.INFO, "handlePUT: content format subject not equal request content format");
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
            } else if (!this.getURI().equals(exchangeURI)) {   // create new topic + sub-topics
                String newTopicPath = createNewTopics(exchange, false);
                if (newTopicPath != null) {
                    LOGGER.log(Level.INFO, "handlePUT: created new topic");
                    exchange.setLocationPath(exchangeURI);
                    exchange.respond(CoAP.ResponseCode.CREATED);
                } else {
                    LOGGER.log(Level.INFO, "handlePUT: could not create new topic");
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
                }

            } else {
                if (exchange.getRequestOptions().hasMaxAge()) { // max lifetime of value;
                    subject.setMaxAgeOfValue(exchange.getRequestOptions().getMaxAge());
                } else {
                    subject.setMaxAgeOfValue(0);
                }
                // Simple Flow Control, ensure not too many Requests
                if (semaphore.tryAcquire(TIMEOUT_MSEC_TOO_MANY_REQUESTS, TimeUnit.MILLISECONDS)) {
                    subject.setState(exchange.getRequestPayload()); // also invokes changed()
                    LOGGER.log(Level.INFO, "handlePUT: Succesfully changed the subject");
                    exchange.respond(CoAP.ResponseCode.CHANGED);
                    semaphore.release();
		    //reenvio a MQTT
                    //actualizarMQTT(exchangeURI.substring(4), exchange.getRequestPayload(),null, 0);
		    protocoloInteraccionCoap.iniciarComunicacion(null, null, exchange, null, null, null);		   

                } else {
                    LOGGER.log(Level.INFO, "handlePUT: Too many requests on subject");
                    exchange.setMaxAge(TIMEOUT_RETRY_SEC_TOO_MANY_REQUESTS);
                    exchange.respond(CoAP.ResponseCode.TOO_MANY_REQUESTS);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "handlePUT: General failure occured. Text: {0}", e);
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
            System.err.println(e);
        }
    }

    /**
     * Sub-Method to handle an incoming POST exchange
     *
     * @param exchange
     */
    protected void handlePost(CoapExchange exchange, boolean isFromRoot) {
        conductBasicTopicGarbageCollection();
        // Auth, otherwise 4.01
	System.out.println("LLEGO AL POST");

        String adaptedPayloadURI = PubSubValidator.adaptURI(this.getURI(), exchange.getRequestText());
        int exchangeContentFormat = exchange.getRequestOptions().getContentFormat();
        try {
            if (checkIfTopicExists(exchange)) {
                LOGGER.log(Level.INFO, "handlePOST: Failed to create new topic, topic already exists");
                exchange.respond(CoAP.ResponseCode.FORBIDDEN);
            } else if (adaptedPayloadURI != null) {
                if (MediaTypeRegistry.getAllMediaTypes().contains(exchangeContentFormat)) {
                    String newTopicPath = createNewTopics(exchange, isFromRoot); //newTopicPath es del tipo: "//ps/topic/subtopic..."
                    if (newTopicPath != null) {
                        LOGGER.log(Level.INFO, "handlePOST: Succesfully created new topic");
                        exchange.setLocationPath(newTopicPath);
                        exchange.respond(CoAP.ResponseCode.CREATED);
			//reenvio a MQTT
			//System.out.println("PAYLOAD: " +exchange.getRequestPayload().toString());
			//actualizarMQTT(newTopicPath.substring(5), exchange.getRequestPayload(), null, 0);
			protocoloInteraccionCoap.iniciarComunicacion(null, null, exchange, null, null, null);		        

                    } else {
                        LOGGER.log(Level.INFO, "handlePOST: Could not create new topic");
                        exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
                    }
                } else {
                    LOGGER.log(Level.INFO, "handlePOST: content format of request is not supported");
                    exchange.respond(CoAP.ResponseCode.NOT_ACCEPTABLE);
                }

            } else {
                LOGGER.log(Level.INFO, "handlePOST: Failed to handle the request");
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "handlePOST: General failure occured. Text: {0}", e);
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
            System.err.println(e);
        }
    }

    /**
     * Sub-Method to handle an incoming DELETE exchange
     *
     * @param exchange
     */
    protected void handleDelete(CoapExchange exchange) {
        String exchangeURI = "/" + exchange.getRequestOptions().getUriPathString();
        // AUTHorizsation check! Otherwise 4.01

        try {
            if (!PUBSUB_API_URI.equals(exchangeURI)) {
                if (this.getURI().equals(exchangeURI)) {
                    try {
                        // send a response to subscribers that topic now deleted -> not found.
                        clearAndNotifyObserveRelations(CoAP.ResponseCode.NOT_FOUND);
                        deleteTopicChildren();
                        LOGGER.log(Level.INFO, "handleDELTE: Deleting of topic (and probable sub-topics) succesful");
                        exchange.respond(CoAP.ResponseCode.DELETED);
                        deleteTopic();
                    } catch (Exception e) {
                        LOGGER.log(Level.INFO, "handleDELTE: Deleting of topic (and probable sub-topics) failed");
                        exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
                    }
                } else {
                    LOGGER.log(Level.INFO, "handleDELTE: Exchange uri is not equal topic uri");
                    exchange.respond(CoAP.ResponseCode.NOT_FOUND);
                }
            } else {
                LOGGER.log(Level.INFO, "handleDELTE: Requested to delete /ps/. Not allowed!");
                exchange.respond(CoAP.ResponseCode.METHOD_NOT_ALLOWED);
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "handleDELTE: General failure occured. Text: {0}", e);
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
            System.err.println(e);
        }
    }

    /**
     * Handle the Observer option (publish, subscribe and unsubscribe) on an
     * incoming exchange
     *
     * @param exchange
     */
    private void handleObserve(CoapExchange exchange) {
        // INFO:
        // when a topic subject is updated (for example PUT) invoke changed() which triggers a exchange.handleRequest which will 
        // lead it to come here again (GET + observe option) so handle it here!
        // UnSub-handling? done by serverMessageDeliverer including (cancel)-message.
        try {
            if (null == exchange.getRequestOptions().getObserve()) {
                LOGGER.log(Level.INFO, "handleGET, OBSERVE: invalid observe option");
            } else {
                ObserveRelation relation = exchange.advanced().getRelation();
                switch (exchange.getRequestOptions().getObserve()) {
                    case 0:
                        // Response to subscribe
                        LOGGER.log(Level.FINE, "handleGET, OBSERVE: observe-option with 0");
                        if (exchange.getRequestOptions().getContentFormat() == this.getContentType()) {

                            if (relation != null) {
                                this.addObserveRelation(relation);
                                LOGGER.log(Level.FINE, "handleGET, OBSERVE: Added an observe relation between {0}:{1} and resource {2}",
                                        new Object[]{exchange.advanced().getRequest().getSource(), exchange.advanced().getRequest().getSourcePort(), this.getURI()});
                                LOGGER.log(Level.FINE, "handleGET, OBSERVE: observe-option with 0, number of observers is {0}", this.getObserverCount());
                            }
                            sendObserveResponse(exchange);
                        } else {
                            // reject subscribing if unequal ct!
                            if (relation != null) {
                                relation.cancel();
                                this.removeObserveRelation(relation);
                                LOGGER.log(Level.FINE, "handleGET, OBSERVE: Removed an observe relation between {0}:{1} and resource {2}",
                                        new Object[]{exchange.advanced().getRequest().getSource(), exchange.advanced().getRequest().getSourcePort(), this.getURI()});
                            }
                            LOGGER.log(Level.INFO, "handleGET, OBSERVE: Unsupported content format of request");
                            exchange.respond(CoAP.ResponseCode.UNSUPPORTED_CONTENT_FORMAT);
                        }
                        break;
                    case 1:
                        // Response to unsubscribe
                        LOGGER.log(Level.FINE, "handleGET, OBSERVE: observe-option with 1, number of observers is {0}", this.getObserverCount());
                        sendObserveResponse(exchange);
                        break;
                    default:
                        LOGGER.log(Level.INFO, "handleGET, OBSERVE: neither subscribe nor unsibscribe");
                        break;
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.INFO, "handleGET, OBSERVE: General failure occured. Text: {0}", e);
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
            System.err.println(e);
        }

    }

    /**
     * Sends a response to an observe-occurence depending on the maxAge of the
     * subject
     *
     * @param exchange
     */
    private void sendObserveResponse(CoapExchange exchange) {
        long subjectTTL = subject.getTopicSubjectTTL();
        if (subjectTTL > 0 || subjectTTL == 0) {
            if (subjectTTL > 0) {
                exchange.setMaxAge((subject.getTopicSubjectTTL() / 1000));
            }
            LOGGER.log(Level.INFO, "handleGET, OBSERVE: Retrieved topic value and responding with payload now");
            exchange.respond(CoAP.ResponseCode.CONTENT, subject.getStateValue(), this.getContentType());
        } else {
            LOGGER.log(Level.INFO, "handleGET, OBSERVE: Outdated subject topic value, responding with No_Content");
            exchange.respond(CoAP.ResponseCode.NO_CONTENT);
        }
    }

    /**
     * Handles a GET exchanges which was identified as READ
     *
     * @param exchange
     */
    private void handleRead(CoapExchange exchange) {
        if (this.getContentType() == exchange.getRequestOptions().getContentFormat()) {
            if (subject.getStateValue() != null && !subject.isTopicSubjectMaxAgeExceeded()) {
                if (subject.getTopicSubjectTTL() > 0) {
                    exchange.setMaxAge((subject.getTopicSubjectTTL() / 1000));
                }
                LOGGER.log(Level.INFO, "handleGET, READ: sucessfully retrieved topic value and responding now");
		String tmpValue = subject.getStateValue().toString();
		String exchangeURI = "/" + exchange.getRequestOptions().getUriPathString();
		/*if(gestorRecursos.comprobarTopic(exchangeURI.substring(4)) !=null){ //entrara cuando sea un GET a un topic externo
			while(tmpValue.equalsIgnoreCase(subject.getStateValue().toString())){
				//System.out.println(tmpValue+"=="+subject.getStateValue().toString());
			}
		}*/
                exchange.respond(CoAP.ResponseCode.CONTENT, subject.getStateValue());
            } else if (subject.isTopicSubjectMaxAgeExceeded() || subject.getStateValue() == null) {
                LOGGER.log(Level.INFO, "handleGET, READ: subject is empty or max age of topic value is exceeded");
                exchange.respond(CoAP.ResponseCode.NO_CONTENT);
            } else {
                LOGGER.log(Level.INFO, "handleGET, READ: Not able to read the topic value");
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
            }
        } else {
            LOGGER.log(Level.INFO, "handleGET, READ: content format request/topic unequal");
            exchange.respond(CoAP.ResponseCode.UNSUPPORTED_CONTENT_FORMAT);
        }
    }

    /**
     * Checks if the topic (including path of topic) does not exist yet.
     *
     * @param exchange
     * @return true if topic exists
     */
    protected boolean checkIfTopicExists(CoapExchange exchange) {
        String pathURI = exchange.getRequestOptions().getUriPathString();
        LOGGER.log(Level.INFO, "checkIfTopicExists: original path {0}", pathURI);
        if (exchange.getRequestCode() == CoAP.Code.POST) {
            pathURI = pathURI + "/" + exchange.getRequestText();
        }
        String toCheckURI = PubSubValidator.removePSfromURI(pathURI);
        LOGGER.log(Level.INFO, "checkIfTopicExists: checking topic for path {0}", toCheckURI);
        if (toCheckURI != null) {
            String[] topics = PubSubValidator.checkPathTopics(toCheckURI.split("/"));
            Resource psResource = getPSrootResource();
            if (psResource != null) {
                LinkedList<String> path = new LinkedList(Arrays.asList(topics));
                Resource current = psResource;
                while (!path.isEmpty() && current != null) {
                    String name = path.removeFirst();
                    current = current.getChild(name);
                }

                LOGGER.log(Level.INFO, "checkIfTopicExists: path/topic exists -> {0}", (current != null));
                return current != null;
            } else {
                LOGGER.log(Level.INFO, "checkIfTopicExists: cloud not find ps-Resource");
                return false;
            }
        } else {
            LOGGER.log(Level.INFO, "checkIfTopicExists: to check Topic URI is empty");
            return true;
        }
    }

    /**
     * Creates a new topic (plus probable subtopics) depending on POST or PUT
     * request. Subtopics are per default in the content format
     * MediaTypeRegistry.APPLICATION_LINK_FORMAT
     *
     * @param exchange
     * @param isFromRoot
     * @return path of the created topic (plus probable subtopics)
     */
    protected String createNewTopics(CoapExchange exchange, boolean isFromRoot) {
        String path;
        boolean payloadHasContent;

        if (exchange.getRequestCode() == CoAP.Code.POST) {
            path = exchange.getRequestText();
            payloadHasContent = false;
        } else {  // it's a PUT message, the path is in the URI 
            payloadHasContent = true;
            if (isFromRoot) {
                path = PubSubValidator.removePSfromURI(exchange.getRequestOptions().getUriPathString());
            } else {
                path = "/" + exchange.getRequestOptions().getUriPathString();
            }
        }
        LOGGER.log(Level.INFO, "CreateNewTopic: ***** path: {0}  *****", path);

        // max_age of topic, if ignored, set Max age to indefinit (zero)
        long tmpMaxAgeTopic;
        if (exchange.getRequestOptions().hasMaxAge()) {
            tmpMaxAgeTopic = (exchange.getRequestOptions().getMaxAge() >= 1) ? exchange.getRequestOptions().getMaxAge() : 0;
        } else {
            tmpMaxAgeTopic = 0;
        }
        try {
            String validPath = PubSubValidator.adaptPath(path);
            if (validPath != null) {
                String[] topics = PubSubValidator.checkPathTopics(validPath.split("/"));
                PubSubTopic[] resources = new PubSubTopic[topics.length];
                if (topics.length > 1) { // we have "intermediate" application/links, different ct format
                    for (int i = 0; i < (topics.length); i++) {
                        if (i == (topics.length - 1)) { // this is the last topic in the path, it has the exchange's ct format
                            resources[i] = new PubSubTopic(topics[i], exchange.getRequestOptions().getContentFormat(), tmpMaxAgeTopic);
                            if (payloadHasContent) {
                                resources[i].subject.setState(exchange.getRequestPayload());
                            }
                        } else {
                            resources[i] = new PubSubTopic(topics[i], MediaTypeRegistry.APPLICATION_LINK_FORMAT, tmpMaxAgeTopic);
                        }
                    }
                    Collections.reverse(Arrays.asList(resources));

                    for (int r = 0; r < (resources.length - 1); r++) {
                        PubSubTopic parent = (PubSubTopic) resources[r + 1];
                        resources[r].setParent(parent);
                        parent.addTopic((PubSubTopic) resources[r]);
                    }
                } else if (topics.length == 1) {
                    resources[0] = new PubSubTopic(topics[0], exchange.getRequestOptions().getContentFormat(), tmpMaxAgeTopic);
                    resources[0].setParent(this);
                    if (payloadHasContent) {
                        resources[0].subject.setState(exchange.getRequestPayload());
                    }
                } else {
                    LOGGER.log(Level.INFO, "CreateNewTopic: invalid split of path");
                    return null;
                }
                resources[(resources.length - 1)].setParent(this);
                addTopic(resources[(resources.length - 1)]);
                String newPath = ("/" + resources[0].getPath() + resources[0].getName());
                LOGGER.log(Level.INFO, "CreateNewTopic: added topic(s) from delivered content, path is {0}", newPath);
                return newPath;
            } else {
                LOGGER.log(Level.INFO, "CreateNewTopic: invalid path");
                return null;
            }

        } catch (Exception e) {
            LOGGER.log(Level.INFO, "CreateNewTopic: General failure, Text: {0}", e);
            System.err.println(e);
            return null;
        }
    }

    /**
     * Set the content type of the topic and the subject belonging to the topic
     *
     * @param contentFormat
     */
    protected final synchronized void setContentType(int contentFormat) {
        this.topicContentFormat = contentFormat;
        getAttributes().addContentType(contentFormat);
    }

    /**
     * Set the content type of the topic and the subject belonging to the topic
     *
     * @return content format of the topic and subject
     */
    protected final int getContentType() {
        return this.topicContentFormat;
    }

    /**
     * clears the content format of the topic and the subject belonging to the
     * topic.
     */
    protected final synchronized void clearContentType() {
        getAttributes().clearContentType();
        this.topicContentFormat = -1;
    }

    /**
     * Gets the 'ps'-Root Resource
     *
     * @return ps-Resource
     */
    private Resource getPSrootResource() {
        Resource current = this;
        if (!this.getName().equals("ps")) {
            do {
                current = current.getParent();
            } while (current.getParent().getName().equals("ps"));
        }
        return current;
    }

    /**
     * Conducts a basic Topic GarbageCollection starting from the 'ps' resource
     *
     */
    public void conductBasicTopicGarbageCollection() {
        LOGGER.log(Level.INFO, "conductBasicTopicGarbageCollection: invoked");
        Resource psResource = getPSrootResource();
        iterateTree((PubSubTopic) psResource);
        LOGGER.log(Level.INFO, "conductBasicTopicGarbageCollection: succesfully conducted");
    }

    /**
     * Iterate the topics-tree and removes a topic if the maxAge is exceeded
     *
     * @param topic
     */
    private void iterateTree(PubSubTopic topic) {
        Collection<Resource> children = topic.getChildren();
        for (Resource child : children) {
            if (child != null) {
                PubSubTopic childTopic = (PubSubTopic) child;
                if (childTopic.subject.isTopicSubjectMaxAgeExceeded()) {
                    LOGGER.log(Level.FINE, "conductBasicTopicGarbageCollection: deleting topic {0}", childTopic.getPath());
                    childTopic.deleteTopic();
                } else {
                    iterateTree(childTopic);
                }
            }
        }

    }

    /**
     * Add a PubSubTopic
     *
     * @param topic
     * @return the created resource
     */
    public CoapResource addTopic(PubSubTopic topic) {
        return add(topic);
    }

    /**
     * Create and add a PubSubTopic
     *
     * @param name
     * @param contentType
     * @param maxAgeTopic
     * @return the created resource
     */
    public CoapResource addTopic(String name, int contentType, long maxAgeTopic) {
        return add(new PubSubTopic(name, contentType, maxAgeTopic));
    }

    /**
     * Delete all children of a topic and notify probable observers.
     */
    public void deleteTopicChildren() {
        Collection<Resource> children = this.getChildren();
        if (!children.isEmpty()) {
            for (Resource child : children) {
                PubSubTopic pubSubChild = (PubSubTopic) child;
                clearAndNotifyObserveRelations(CoAP.ResponseCode.NOT_FOUND);
                pubSubChild.deleteTopicChildren();
            }
        }
        deleteTopic();
    }

    /**
     * Delete the topic.
     */
    public void deleteTopic() {
        this.delete();
    }

    public void setProtocoloInteraccionCoap(ProtocoloInteraccionCoap _protocoloInteraccionCoap){
	System.out.println("Soy el recurso PS y estoy en setprotocoloInteraccionCoap()");
	if(_protocoloInteraccionCoap == null){
		System.out.println("protocoloInteraccionCoap NUUUUUUUUUL");	
	}
	protocoloInteraccionCoap = _protocoloInteraccionCoap;
	setProtocoloInteraccionSuper(_protocoloInteraccionCoap);
    }
    
    public String getSubjectStaveValue(){
	return subject.getStateValue().toString();
    }
    //
    // ------- Topic Subject ----------//
    //
    //
    /**
     * This class contains the subject to manage the value of the topic and
     * manage the maxAge.
     */
    private class TopicSubject {

        private byte[] stateValue = null;  // byte[] or string
        private long maxAgeOfValue = 0;
        private long lastUpdatedTimestamp;

        public TopicSubject() {
            this.stateValue = null;
            updateTopicSubjectTTL();
        }

        public TopicSubject(byte[] state, int contentFormat) {
            this.stateValue = state;
            updateTopicSubjectTTL();
        }

        public byte[] getStateValue() {
            return stateValue;
        }

        public synchronized void setState(byte[] state) {
            this.stateValue = state;
            updateTopicSubjectTTL();
            changed(); // notify the observers.
        }

        public synchronized void clearState() {
            stateValue = null;
            updateTopicSubjectTTL();
        }

        public long getMaxAgeOfValue() {
            return maxAgeOfValue;
        }

        public long getMaxAgeOfValueInSeconds() {
            return (maxAgeOfValue / 1000);
        }

        /**
         * Set maxAge in seconds
         *
         * @param maxAgeOfValue time in seconds
         */
        public void setMaxAgeOfValue(long maxAgeOfValue) {
            if (maxAgeOfValue >= 0) {
                // max_Age in message is in seconds, here handled in milliseconds
                this.maxAgeOfValue = (maxAgeOfValue * 1000);
            }
        }

        public final synchronized void updateTopicSubjectTTL() {
            lastUpdatedTimestamp = System.currentTimeMillis();
        }

        /**
         * A value of 0 means indefinite TTL for the subject
         *
         * @return time to live in milliseconds, zero means indefinite lifetime.
         */
        public synchronized long getTopicSubjectTTL() {
            if (maxAgeOfValue == 0) {
                return 0;
            }
            return (lastUpdatedTimestamp + maxAgeOfValue) - System.currentTimeMillis();
        }

        public synchronized boolean isTopicSubjectMaxAgeExceeded() {
            if (maxAgeOfValue == 0) {
                return false;
            }
            return (getTopicSubjectTTL() < 0);
        }

    }
}
