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

	//private static final int COAP_PORT = NetworkConfig.getStandard().getInt(NetworkConfig.Keys.COAP_PORT);
	private int COAP_PORT = 5683;
	private CoapPubSubClient client;
	private final String BASE_URI;
	
	public PrintWriter pw;
	public String hostIP;
        public Boolean conCoap;

	public ClientePubSub(String connectorChoice, PrintWriter _pw, String _hostIP, Boolean _conCoap){
	
		if(_hostIP.contains(":")){
			hostIP = _hostIP.split(":")[0];
			COAP_PORT =Integer.parseInt( _hostIP.split(":")[1]);
		}else{
			hostIP = _hostIP;
		}
		
		conCoap = _conCoap;
		pw = _pw;

		client = new CoapPubSubClient();

		NetworkConfig config = NetworkConfig.getStandard();

		Connector connector = null;
		switch (connectorChoice) {
		    case "udp":
		        break;
		   case "dtls":
		        connector = new DtlsClientConnector("certsCoAP/keyStore.jks", "certsCoAP/trustStore.jks", 2000);
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
		
		if(conCoap){
		    client.useCONs();
		}else{
		    client.useNONs();
		}
	}

        public void crearTopic(String nombre) {
		// Crea un nuevo sub-topic
		client.setURI(BASE_URI);

		client.create(new CoapHandler() {
		    @Override
		    public void onLoad(CoapResponse response) {
		        System.out.println("Codigo de respuesta a la peticion de crear topic: "+ response.getCode());
		    }

		    @Override
		    public void onError() {
		        System.err.println("ERROR al crear el topic" );
		    }

		}, nombre, MediaTypeRegistry.TEXT_PLAIN);
    	}

	public void subscribe(String nombre){
		client.setURI(BASE_URI + nombre);

		client.subscribe(new CoapHandler() {
		    @Override
		    public void onLoad(CoapResponse response) {
			//obtengo el timestamp en el que llega el mensaje
			long t2 = System.currentTimeMillis();
		        System.out.println("Sub: '" + response.getResponseText() + "'");

			String contenido= new String(response.getResponseText());
			long t1 = Long.parseLong(contenido);
			long diff = Math.abs(t2 - t1);
			System.out.println("Reatardo: " + String.valueOf(diff) + " ms");

			try{
			    pw.println(String.valueOf(diff));
			}catch(Exception e){
			}
		    }

		    @Override
		    public void onError() {
		        System.err.println("ERROR AL SUSCRIBIRSE ");
		    }

		}, MediaTypeRegistry.TEXT_PLAIN);

	}

	public void publish(String nombre, String contenido){
		client.setURI(BASE_URI + nombre);
		client.publish(contenido, MediaTypeRegistry.TEXT_PLAIN).getCode();
		System.out.println("Pub topic " + nombre + " " + contenido);

	}

        public void enviarGet(String nombre){


	    client.setURI(BASE_URI + nombre);


	    long t1 = System.currentTimeMillis();

	    CoapResponse response = client.read(MediaTypeRegistry.TEXT_PLAIN);

	    long t2 = System.currentTimeMillis();

	    System.out.println("RESPUESTA DEL GET: " + response.getResponseText());

	    //Calculo el retardo en milisegundos
	    long diff = Math.abs(t2 - t1);

	    System.out.println("Reatardo: " + String.valueOf(diff) + " ms");
	    pw.println(String.valueOf(diff));

	    client.shutdown();
	

        }

}

		

		
		
