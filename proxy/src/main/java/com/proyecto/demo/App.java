package com.proyecto.demo;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import io.moquette.server.config.FileResourceLoader;
import io.moquette.server.config.IResourceLoader;
import io.moquette.server.config.ResourceLoaderConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.InterceptHandler;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.server.Server;
import io.moquette.server.config.IConfig;


import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

import javax.net.ssl.SSLContext;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.resources.CoapExchange;
//import org.eclipse.californium.elements.tcp.netty.TcpServerConnector;
//import org.eclipse.californium.elements.util.NetworkInterfacesUtil;

import java.util.concurrent.TimeUnit;
import java.lang.InterruptedException;

import java.util.*;
import java.util.Map.Entry;

import com.sun.net.httpserver.*;
import java.net.*;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.IOException;

import com.sun.net.httpserver.*;
import java.util.concurrent.ThreadPoolExecutor;

import java.util.concurrent.Semaphore;

import java.util.concurrent.TimeUnit;
import java.lang.InterruptedException;

import java.io.File;

import com.proyecto.*;

import java.util.concurrent.TimeUnit;

public class App 
{	

	//public static PubSubBroker servidorCoAP;
	//public static HttpServer servidorHTTP;
	public static GestorRecursos gestorRecursos;
	public static ProtocoloInteraccionCoap protocoloInteraccionCoap;
	public static ProtocoloInteraccionMqtt protocoloInteraccionMqtt;
	public static ProtocoloInteraccionHTTP protocoloInteraccionHTTP;
	
	public static Boolean sec;

	public static Semaphore mutex = new Semaphore(1, true); //mutex para controlar el acceso a las listas de topics (True=fair)

	
	public static void main(String[] args) throws InterruptedException, IOException {
		sec = false;
		
		if(args.length > 0){
		    if(args[0].equals("sec")){
			sec = true;
		    }
		    
		}
			

		
		// Parametros de configuracion de los servidores/brokers

		// CoAP
		String[] connectorChoice = new String[] {"udp", "dtls"};
		int[] coap_port = new int[] {5683, 5684};

		// HTTP		
		int puertoHTTP = 8888; //CONFLICTO CON WS DE MQTT config/moquette.conf
		int puertoHTTPs = 9999; //CONFLICTO CON WS DE MQTT config/moquette.conf

		// MQTT
		File fileSetings = new File("src/main/resources/config/moquette.conf");
		IResourceLoader fylesystemLoader = new FileResourceLoader(fileSetings);
		IConfig config = new ResourceLoaderConfig(fylesystemLoader);
		

		try {
			
			// Creo el broker que gestiona peticiones CoAP
			final PubSubBroker servidorCoAP = new PubSubBroker(connectorChoice, coap_port);
	       		servidorCoAP.addEndpoints();
	       		

			// Creo el servidor que gestiona peticiones HTTP
			final HttpServer servidorHTTP = HttpServer.create(new InetSocketAddress(puertoHTTP), 0);			
			final HttpsServer servidorHTTPs = HttpsServer.create(new InetSocketAddress(puertoHTTPs), 0);
	

			SSLUtilHTTP sslUtil = new SSLUtilHTTP("password", "src/main/resources/certsHTTP/serverkeystore.jks", "TLSv1.2", "SunX509");
			SSLContext sc = sslUtil.getSSLContext();
			servidorHTTPs.setHttpsConfigurator(new HttpsConfigurator(sc));
			


			// Creating a MQTT Broker using Moquette
			final Server mqttBroker = new Server();
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					System.out.println("Deteniendo servidores MQTT, CoAP y HTTP");
					mqttBroker.stopServer();
					servidorCoAP.stop();
					servidorHTTP.stop(0);
					servidorHTTPs.stop(0);
					System.out.println("Servidores detenidos");
				}
			});



			// Creo el gestorRecursos despues de crear todos los servidores para evitas que sea null
			gestorRecursos = new GestorRecursos( mutex, servidorCoAP,  servidorHTTP, mqttBroker, sec);
	

			// Creo las instancias de los protocolos de interaccion para CoAP, MQTT y HTTP
			protocoloInteraccionCoap = new ProtocoloInteraccionCoap(gestorRecursos, mutex, sec);
			protocoloInteraccionMqtt = new ProtocoloInteraccionMqtt(gestorRecursos, mutex, sec);
			protocoloInteraccionHTTP = new ProtocoloInteraccionHTTP(gestorRecursos, mutex, sec);

			//iniciar el root handler que gestionara las peticiones de los topics
			HTTPHandler httpHandlerRoot = 	new HTTPHandler("/", "sin iniciar", protocoloInteraccionHTTP);	
			servidorHTTP.createContext("/", httpHandlerRoot);
			servidorHTTPs.createContext("/", httpHandlerRoot);
			
			gestorRecursos.setHttpRootHandler(httpHandlerRoot);

			//Establezco el protocolo de interaccion en el servidor/broker CoAP
			servidorCoAP.setProtocoloInteraccionCoap(protocoloInteraccionCoap);//se le asigna el gestor de recursos al server una vez creado el server para evitar el problema de nullpointer en la referencia

			// Arranco todos los servidores/brokers
			servidorCoAP.start();		
			servidorHTTP.start();
			servidorHTTPs.start();

			final List<? extends InterceptHandler> userHandlers = Arrays.asList(new PublisherListener(protocoloInteraccionMqtt));
			mqttBroker.startServer(config, userHandlers);

			System.out.println("Servidores corriendo, presionar ctrl-c para detenerlo..");

			
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		
		

		while(true){

		}
		
		
		

	}


	
	

}
