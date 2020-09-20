package com.proyecto;

import java.io.*;

import com.wiss.thom.elements.dtls.DtlsClientConnector;
import com.wiss.thom.elements.sctp.SctpClientConnector;
import com.wiss.thom.elements.ws.WsClientConnector;
import com.wiss.thom.elements.wss.WssClientConnector;

import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapPubSubClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.tcp.TcpClientConnector;
import org.eclipse.californium.elements.tcp.TlsClientConnector;



public class ClienteSubObservador{

	private static final int COAP_PORT = NetworkConfig.getStandard().getInt(NetworkConfig.Keys.COAP_PORT);
	private CoapPubSubClient client;
	private final String BASE_URI;
	


	public ClienteSubObservador(String connectorChoice, String hostIP){
		

		client = new CoapPubSubClient();

		NetworkConfig config = NetworkConfig.getStandard();

		Connector connector = null;
		switch (connectorChoice) {
		    case "udp":
		        break;
		   case "dtls":
		        connector = new DtlsClientConnector("certificados/keyStore.jks", "certificados/trustStore.jks", 2000);
		        break;
		    default:
		        break;
		}

		CoapEndpoint endpoint = null;
		if (connector == null) {
		    endpoint = new CoapEndpoint(config);
		} else {
		    endpoint = new CoapEndpoint(connector, config);
		}

		client.setEndpoint(endpoint);

		BASE_URI = "coap://" + hostIP  + "/"; //hostIP debe ser del formato IP:puerto
		client.setURI(BASE_URI);
		client.useCONs();

	}

 
	public void subscribe(final String nombre){
		client.setURI(BASE_URI + nombre);

		client.subscribe(new CoapHandler() {
		    @Override
		    public void onLoad(final CoapResponse response) {

			Thread tmpThread = new Thread(){
				public void run(){
				
			
					// Creo un Cliente que envie un PUT
					ClientePubSub client2 = new ClientePubSub( "udp", "127.0.0.1"); // se conecta al broker
					
					client2.publish(nombre, response.getResponseText());
					client2.desconectar();
				}
			};
			tmpThread.start();

		    }

		    @Override
		    public void onError() {
		        System.err.println("ERROR AL SUSCRIBIRSE ");
		    }

		}, MediaTypeRegistry.TEXT_PLAIN);

	}
		
	public void setUri(String _uri){

		client.setURI(BASE_URI + _uri);

	}

	public void publish(String nombre, String contenido){

		client.setURI(BASE_URI + nombre);
		client.publish(contenido, MediaTypeRegistry.TEXT_PLAIN);
		
	}

        public CoapResponse enviarGet(String nombre){

	    client.setURI(BASE_URI + nombre);

	    CoapResponse response = client.read(MediaTypeRegistry.TEXT_PLAIN);

	    return response;
	

        }

	public void desconectar(){
		client.shutdown();
	}
	
	public String getUri(){
		return client.getURI();
	}

}

		

		
		
