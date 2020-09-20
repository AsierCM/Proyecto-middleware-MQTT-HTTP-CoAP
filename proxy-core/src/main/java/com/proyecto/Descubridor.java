package com.proyecto;

import  java.util.List;
import java.util.ArrayList;

import java.lang.Thread;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.TimeUnit;
import java.lang.InterruptedException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
//import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.californium.core.network.config.NetworkConfig;
//import org.eclipse.californium.core.network.config.NetworkConfigDefaultHandler;
import org.eclipse.californium.core.network.config.NetworkConfig.Keys;
import org.eclipse.californium.core.coap.MediaTypeRegistry;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.Semaphore;
import java.lang.InterruptedException;


public class Descubridor extends Thread{
	
	public String dirServidor;
	public GestorRecursos gestorRecursos;
	public Semaphore mutex;
	public Boolean sec;	
	
	private static final File CONFIG_FILE = new File("Californium.properties");
	private static final String CONFIG_HEADER = "Californium CoAP Properties file for Fileclient";
	private static final int DEFAULT_MAX_RESOURCE_SIZE = 2 * 1024 * 1024; // 2 MB
	private static final int DEFAULT_BLOCK_SIZE = 512;

	
	public Descubridor(String _dirServidor, GestorRecursos _gestorRecursos, Semaphore _mutex, Boolean _sec){

		dirServidor= _dirServidor;
		gestorRecursos = _gestorRecursos;
		mutex = _mutex;
		sec = _sec;

		System.out.println("SEC5: " + String.valueOf(sec));
	}
	
	public void run(){
		
		System.out.println("Comienza el primer Thread");

		String connectorChoice = "udp";
		if(sec){
		    connectorChoice = "dtls";
		}
		
		try{
		    Thread.sleep(2000);
		}catch(InterruptedException e){
		}
		
		
		String uri = new String("coap://"+dirServidor+"/.well-known/core");

		//construyo el cliente coap
		ClientePubSub client = new ClientePubSub(connectorChoice, dirServidor);

		
		CoapResponse response = client.descubrir();
		
		
		// Analizo la respuesta del .well-known/core que descubre todos los recursos que tiene el servidor
		if (response!=null) {

			String[] items = (response.getResponseText()).split(",");
	
			for(int i=0;i<items.length;i++){
				if(!items[i].contains("</>") && !items[i].contains("ct=40") && !items[i].contains("</.well-known/core>")){ // quitamos la linea de informacion general	y topics que no sean del tipo: plain-text
					Boolean observable = items[i].contains(";obs");
					String nombre = (items[i].split(">")[0]).substring(2);

					try{
						mutex.acquire();
						propagarProxys(nombre, "vacio");
						crearTopicBroker(nombre, observable);
						mutex.release();

					}catch(InterruptedException e){
					}
					
				}
				try{
					Thread.sleep(5000);
				}catch(InterruptedException e){
				}
			}
				
		} else {
			System.out.println("No se recibe respuesta");
		}
		client.desconectar();
		
		
	}

	public void crearTopicBroker(String _nombre, Boolean observable){


		String connectorChoice = "udp"; 
		ClientePubSub cliente = new ClientePubSub(connectorChoice, "127.0.0.1");
		String BASE_URI = "coap://127.0.0.1:5683/ps/";
		String path = null;		

		String[] parts = _nombre.split("/");

		if(gestorRecursos.comprobarTopic(_nombre)==null){ 
			
			if(parts.length > 1){
			    // Primero creo de uno en uno desde el raiz
			    if(gestorRecursos.comprobarTopic(parts[0])==null){
			        
			        gestorRecursos.informarTopicTotal(null, observable, parts[0], "mqtt",null);	
				gestorRecursos.informarTopic(dirServidor, observable, parts[0]);
				cliente.crearTopic(parts[0], MediaTypeRegistry.APPLICATION_LINK_FORMAT);
			    }
		            for(int i=1; i<=parts.length-1; i++){
				
			        for( int j=0; j<i; j++){

				    if(j==0){
					path = parts[0] + "/";
				    }else{
					path = path + parts[j] + "/";
				    }

				}
				cliente.setUri(path);
				if(i != parts.length-1){
					if(gestorRecursos.comprobarTopicTotal(path+parts[i])==null){
					
						gestorRecursos.informarTopicTotal(null, false, path+parts[i], "mqtt",null);
						gestorRecursos.informarTopic(dirServidor, false, path+parts[i]);
						cliente.crearTopic(parts[i], MediaTypeRegistry.APPLICATION_LINK_FORMAT);
					}

				}else{
				        
					gestorRecursos.informarTopicTotal(null, observable, path+parts[i], "mqtt",null);
					gestorRecursos.informarTopic(dirServidor, observable, path+parts[i]);
					cliente.crearTopic(parts[i], MediaTypeRegistry.TEXT_PLAIN);
					cliente.setUri(path+parts[i]);
				}			
				
			    }
			}else{
			    if(gestorRecursos.comprobarTopic(parts[0])==null){
			        
			        gestorRecursos.informarTopicTotal(null, observable, parts[0], "mqtt",null);	
				gestorRecursos.informarTopic(dirServidor, observable, parts[0]);
				cliente.crearTopic(parts[0], MediaTypeRegistry.TEXT_PLAIN);
			    }
			}
				
				
		}


	
	}


	public void propagarProxys(String _nombre, String _contenido){
	    ArrayList listaProxys = gestorRecursos.getListaProxys();
		
	    if(gestorRecursos.comprobarTopicTotal(_nombre)==null){ //si entra aqui es porque no existe y es la primera vez que se recibe
		for(int i=0; i<=listaProxys.size()-1;i++){
		
		    String broker = ((String)listaProxys.get(i)).split(" ")[1];
	
		    try{
		  	    MqttClient client2 = new MqttClient("tcp://" + broker, "Proxy-1",new MemoryPersistence());
			    client2.connect();
			    MqttMessage message = new MqttMessage(_contenido.getBytes());
			    message.setQos(0);
			    client2.publish(_nombre,message);
			    client2.disconnect();
		    }catch(MqttException me){
			    System.out.println(me.getReasonCode());
			    System.out.println(me.getMessage());
			    System.out.println(me.getLocalizedMessage());
			    System.out.println(me.getCause());
			    System.out.println(me);
		    }
		}
	    }
	}
			

}
