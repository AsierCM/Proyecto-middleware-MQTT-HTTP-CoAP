/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.eclipse.californium.core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An example CoapHandler for the special purpose of a PubSub subscription.
 *
 * @author Thoams Wiss
 */
public abstract class CoapSubscribeHandler implements CoapHandler {

    /**
     * The logger
     */
    private static final Logger LOGGER = Logger.getLogger(CoapSubscribeHandler.class.getCanonicalName());

    @Override
    public void onLoad(CoapResponse response) {
        String responseCode = response.getCode().toString();
        if (responseCode != null) {
            switch (responseCode) {
                case "2.04":
                    onChanged(response);
                    break;
                case "2.05":
                    onContent(response);
                    break;
                case "2.07":
                    onNoContent(response);
                    break;
                case "4.00":
                    onBadRequest(response);
                    break;
                case "4.01":
                    onUnauthorized(response);
                    break;
                case "4.04":
                    onTopicNotFound(response);
                    break;
                case "4.15":
                    onUnsupportedContentFormat(response);
                    break;
                default:
                    onBadRequest(response);
                    break;
            }
        } else {
            LOGGER.log(Level.INFO, "ResponseCode in onLoad was null");
            onBadRequest(response);
        }
    }

    @Override
    public void onError() {
        LOGGER.log(Level.SEVERE, "Error occured");
        System.err.println("Error occured in class " + CoapSubscribeHandler.class.getCanonicalName());
    }

    public void onCreated(CoapResponse response) {
    }

    public void onContent(CoapResponse response) {

    }

    public void onNoContent(CoapResponse response) {

    }

    public void onChanged(CoapResponse response) {

    }

    public void onBadRequest(CoapResponse response) {

    }

    public void onUnauthorized(CoapResponse response) {

    }

    public void onUnsupportedContentFormat(CoapResponse response) {

    }

    public void onTopicNotFound(CoapResponse response) {

    }

}
