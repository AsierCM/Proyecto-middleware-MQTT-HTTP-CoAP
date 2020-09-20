/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.eclipse.californium.core;

import java.util.Set;

/**
 * The API describing all possible operations for PubSub clients and brokers.
 * @author Thomas Wiss
 */
public interface CoapPubSubAPI {

    /**
     * Discovers the tree of topics on the PubSubAPI (/ps/)
     *
     * @return a set with weblinks containing the discovered topics
     */
    public Set<WebLink> discover();

    /**
     * Discovers the tree of topics on the PubSubAPI (/ps/) with specified query
     *
     * @param query
     * @return a set with weblinks containing the discovered topics
     */
    public Set<WebLink> discover(String query);

    /**
     * Create a topic with probable subtopics specified in the payload with a
     * certain content format. Lifetime of the topic (and probable subtopics) is
     * indefinite (MaxAge of 0 (zero)).
     *
     * @param payload
     * @param contentFormat
     * @return the response
     */
    public CoapResponse create(byte[] payload, int contentFormat);

    /**
     * Create a topic with probable subtopics specified in the payload with a
     * certain content format. Lifetime of the topic (and probable subtopics) is
     * as specified by the maxAge in seconds.
     *
     * @param payload
     * @param contentFormat
     * @param maxAgeOfTopic in seconds
     * @return the response
     */
    public CoapResponse create(byte[] payload, int contentFormat, long maxAgeOfTopic);

    /**
     * Create a topic with probable subtopics specified in the payload with a
     * certain content format. Lifetime of the topic (and probable subtopics) is
     * indefinite (MaxAge of 0 (zero)).
     *
     * @param payload
     * @param contentFormat
     * @return the response
     */
    public CoapResponse create(String payload, int contentFormat);

    /**
     * Create a topic with probable subtopics specified in the payload with a
     * certain content format. Lifetime of the topic (and probable subtopics) is
     * as specified by the maxAge in seconds.
     *
     * @param payload
     * @param contentFormat
     * @param maxAgeOfTopic in seconds
     * @return the response
     */
    public CoapResponse create(String payload, int contentFormat, long maxAgeOfTopic);

    /**
     * Asynchronous call of the create method with a specified payload and a
     * certain content format. Lifetime of the topic (and probable subtopics) is
     * indefinite.
     *
     * @param handler
     * @param payload
     * @param contentFormat
     */
    public void create(CoapHandler handler, byte[] payload, int contentFormat);

    /**
     * Asynchronous call of the create method with a specified payload and a
     * certain content format. Lifetime of the topic (and probable subtopics) is
     * indefinite.
     *
     * @param handler
     * @param payload
     * @param contentFormat
     */
    public void create(CoapHandler handler, String payload, int contentFormat);

    /**
     * Asynchronous call of the create method with a specified payload and a
     * certain content format. Lifetime of the topic (and probable subtopics) is
     * as specified in maxAge.
     *
     * @param handler
     * @param payload
     * @param contentFormat
     * @param maxAgeOfTopic in seconds
     */
    public void create(CoapHandler handler, byte[] payload, int contentFormat, long maxAgeOfTopic);

    /**
     * Asynchronous call of the create method with a specified payload and a
     * certain content format. Lifetime of the topic (and probable subtopics) is
     * as specified in maxAge.
     *
     * @param handler
     * @param payload
     * @param contentFormat
     * @param maxAgeOfTopic in seconds
     */
    public void create(CoapHandler handler, String payload, int contentFormat, long maxAgeOfTopic);

    /**
     * Publish a specified payload and a certain content format. If the topic
     * does not exist it will be created under the specified URI under which it
     * is called including probable subtopics. Lifetime of the topic (and
     * probable subtopics) is indefinite.
     *
     * @param payload
     * @param contentFormat
     * @return the response
     */
    public CoapResponse publish(byte[] payload, int contentFormat);

    /**
     * Publish a specified payload and a certain content format. If the topic
     * does not exist it will be created under the specified URI under which it
     * is called including probable subtopics. Lifetime of the topic (and
     * probable subtopics) is as in MaxAge
     *
     * @param payload
     * @param contentFormat
     * @param maxAgeOfTopic in seconds
     * @return the response
     */
    public CoapResponse publish(byte[] payload, int contentFormat, long maxAgeOfTopic);

    /**
     * Publish a specified payload and a certain content format. If the topic
     * does not exist it will be created under the specified URI under which it
     * is called including probable subtopics. Lifetime of the topic (and
     * probable subtopics) is indefinite.
     *
     * @param payload
     * @param contentFormat
     * @return the response
     */
    public CoapResponse publish(String payload, int contentFormat);

    /**
     * Publish a specified payload and a certain content format. If the topic
     * does not exist it will be created under the specified URI under which it
     * is called including probable subtopics. Lifetime of the topic (and
     * probable subtopics) is as in MaxAge.
     *
     * @param payload
     * @param contentFormat
     * @param maxAgeOfTopic
     * @return the response
     */
    public CoapResponse publish(String payload, int contentFormat, long maxAgeOfTopic);

    /**
     * Asynchronous call of the publish method with a specified payload and a
     * certain content format. If the topic does not exist it will be created
     * under the specified URI under which it is called including probable
     * subtopics. Lifetime of the topic (and probable subtopics) is indefinite.
     *
     * @param handler
     * @param payload
     * @param contentFormat
     */
    public void publish(CoapHandler handler, byte[] payload, int contentFormat);

    /**
     * Asynchronous call of the publish method with a specified payload and a
     * certain content format. If the topic does not exist it will be created
     * under the specified URI under which it is called including probable
     * subtopics. Lifetime of the topic (and probable subtopics) is indefinite.
     *
     * @param handler
     * @param payload
     * @param contentFormat
     */
    public void publish(CoapHandler handler, String payload, int contentFormat);

    /**
     * Asynchronous call of the publish method with a specified payload and a
     * certain content format. If the topic does not exist it will be created
     * under the specified URI under which it is called including probable
     * subtopics. Lifetime of the topic (and probable subtopics) is as in MaxAge
     *
     * @param handler
     * @param payload
     * @param contentFormat
     * @param maxAgeOfTopic in seconds
     */
    public void publish(CoapHandler handler, byte[] payload, int contentFormat, long maxAgeOfTopic);

    /**
     * Asynchronous call of the publish method with a specified payload and a
     * certain content format. If the topic does not exist it will be created
     * under the specified URI under which it is called including probable
     * subtopics. Lifetime of the topic (and probable subtopics) is as in MaxAge
     *
     * @param handler
     * @param payload
     * @param contentFormat
     * @param maxAgeOfTopic in seconds
     */
    public void publish(CoapHandler handler, String payload, int contentFormat, long maxAgeOfTopic);

    /**
     * Asynchronous subscribe to the specified topic of the current client URI
     * with the correct content format of the topic.
     *
     * @param handler
     * @param contentFormat
     * @return the CoapPubSubObserveRelation handling the subscription
     */
    public CoapPubSubObserveRelation subscribe(CoapHandler handler, int contentFormat);

    /**
     * Unsubscribe the topic of the current client URI.
     *
     * @return the response
     */
    public CoapResponse unsubscribe();

    /**
     * Synchronous read operation of the topic of the current client URI with
     * the correct content format of the topic.
     *
     * @param contentFormat
     * @return the response
     */
    public CoapResponse read(int contentFormat);

    /**
     * Asynchronous read operation of the topic of the current client URI with
     * the correct content format of the topic.
     *
     * @param handler
     * @param contentFormat
     */
    public void read(CoapHandler handler, int contentFormat);

    /**
     * Synchronous remove read operation of the topic of the current client URI.
     *
     * @return the response
     */
    public CoapResponse remove();

    /**
     * Asynchronous remove read operation of the topic of the current client
     * URI.
     *
     * @param handler
     */
    public void remove(CoapHandler handler);

}
