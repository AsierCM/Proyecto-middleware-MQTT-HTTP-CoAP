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



public class ClientePubSub{

	private int COAP_PORT;	
	private CoapPubSubClient client;
	private String BASE_URI;
	private String hostIP;
	


	public ClientePubSub(String connectorChoice, String _hostIP){
		

		client = new CoapPubSubClient();

		NetworkConfig config = NetworkConfig.getStandard();

		
		if(_hostIP.split(":").length >1){
		    hostIP = _hostIP.split(":")[0];
		    COAP_PORT = Integer.parseInt(_hostIP.split(":")[1]);
		}else{
		    hostIP = _hostIP;
		    COAP_PORT = 5683;
		}

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

		BASE_URI = "coap://" + hostIP + ":" + COAP_PORT + "/ps/";
		client.setURI(BASE_URI);
		client.useCONs();

	}

        public void crearTopic(String nombre, int contentFormat) {
		
		System.out.println("CREO EL TOPIC DE NOMBRE: " +nombre);
		client.create(new CoapHandler() {
		    @Override
		    public void onLoad(CoapResponse response) {
		        System.out.println("Codigo de respuesta a la peticion de crear topic: "+ response.getCode());
		    }

		    @Override
		    public void onError() {
		        System.err.println("ERROR al crear el topic" );
		    }

		}, nombre, contentFormat);
    	}

	
		
	public void setUri(String _uri){
		client.setURI(BASE_URI + _uri);
	}

	public void publish(String nombre, String contenido){
		client.setURI(BASE_URI + nombre);
		client.publish(contenido, MediaTypeRegistry.TEXT_PLAIN);
		
	}

	public String enviarGet(String nombre){

	    BASE_URI = "coap://" + hostIP + ":" + COAP_PORT + "/";
	    client.setURI(BASE_URI + nombre);

	    CoapResponse response = client.read(MediaTypeRegistry.TEXT_PLAIN);

	    client.shutdown();

	    return response.getResponseText();
	

        }

	public CoapResponse descubrir(){
	
	    BASE_URI = "coap://" + hostIP + ":" + COAP_PORT + "/";
	    client.setURI(BASE_URI + ".well-known/core");

	    CoapResponse response = client.read(MediaTypeRegistry.TEXT_PLAIN);

	    client.shutdown();

	    return response;
	

        }

	public void desconectar(){
		client.shutdown();
	}
	
	public String getUri(){
		return client.getURI();
	}

}

		

		
		
