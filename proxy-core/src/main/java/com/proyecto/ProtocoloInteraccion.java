package com.proyecto;

import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.network.Exchange;

import io.moquette.interception.messages.InterceptPublishMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.interception.messages.InterceptConnectionLostMessage;

import com.sun.net.httpserver.HttpExchange;

import java.util.concurrent.Semaphore;




public interface ProtocoloInteraccion{


	public Boolean iniciarComunicacion(InterceptPublishMessage pubMsg, InterceptSubscribeMessage subMsg, CoapExchange coapExchange, Exchange exchange, String tipoAccion, HttpExchange he);

	public void cancelarComunicacion(String clientId);


}	
