package com.proyecto;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.IOException;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;

import io.moquette.server.Server;
import io.moquette.spi.impl.subscriptions.Subscription;


import java.util.concurrent.TimeUnit;
import java.lang.InterruptedException;
import java.util.concurrent.Semaphore;

import org.eclipse.californium.core.network.Exchange;

public class GestorRecursos{
	
	public ArrayList<Topic> topicList; //lista destinada a topics CoAP de servidores externos
	public ArrayList<Topic> topicListTotal; //lista que almacena la informacion de todos los topics existentes tanto MQTT como CoAP
	public ArrayList<TopicExterno> topicListExternos = new ArrayList<TopicExterno>();

	// lista de brokers distribuidos
	public ArrayList<String> brokersMQTTExternos = new ArrayList<String>();
	 
	public PubSubBroker servidorCoap;
	public HttpServer servidorHTTP;
	public Server servidorMqtt;
	public GestorDescubridores gestorDescubridores;
	public HTTPHandler httpHandlerRoot;
	public Semaphore mutex;
	public Boolean sec;

	public GestorRecursos(Semaphore _mutex, PubSubBroker _servidorCoap, HttpServer _servidorHTTP, Server _servidorMqtt, Boolean _sec){
		topicList =  new ArrayList<Topic>();
		topicListTotal =  new ArrayList<Topic>();

		servidorCoap = _servidorCoap;
		servidorHTTP = _servidorHTTP;
		servidorMqtt = _servidorMqtt;
		mutex = _mutex;
		sec = _sec;

		arrancarDescubridores();

		brokersMQTTExternos.add("Proxy-0 192.168.1.6:7777"); //lista de brokers, ID que usara en el client. IP:puerto del broker MQTT
		

	}

	public void setHttpRootHandler(HTTPHandler _httpHandlerRoot){
		httpHandlerRoot = _httpHandlerRoot;
	}	

        public void arrancarDescubridores(){
		gestorDescubridores = new GestorDescubridores(this, mutex, sec);
		gestorDescubridores.start();
	}

	public void actualizarRootServidorHTTP(){

		String respuesta = "Topics disponibles:\n";
		for(int i =0; i<topicListTotal.size(); i++){

			respuesta = respuesta + "Nombre: " +topicListTotal.get(i).name + "; Path: "+ topicListTotal.get(i).name +"\n";
			
		}

		httpHandlerRoot.actualizar(respuesta);
	}

	public ArrayList getListaProxys(){
		return brokersMQTTExternos;
	}

	public void informarTopic(String _dirServidor, Boolean _observable, String _nombre){ //para topics de servidores coap externos

		//compruebo si el topic ya existe en la lista, si no existe lo creo
		if(!topicList.isEmpty()){
			for(int i=0; i<=topicList.size(); i++){
				
				if((topicList.get(i)).name.equalsIgnoreCase(_nombre)){ //si ya existe ese topic
					//no hago nada
					break;
				}
				if(i==topicList.size()-1){ //si estas en la ultima iteracion y no hay coincidencias, no existe
					topicList.add(new Topic(_nombre, _observable, _dirServidor, "coap", null));
					break;
				}
				
			}
		}else{
			topicList.add(new Topic(_nombre, _observable, _dirServidor, "coap", null));
			
		}
		
		
	}


	public void informarTopicExterno(String _dirServidor, String _nombre){ //para topics de servidores coap externos

		//compruebo si el topic ya existe en la lista, si no existe lo creo
		if(!topicListExternos.isEmpty()){
			for(int i=0; i<=topicListExternos.size(); i++){
				
				if((topicListExternos.get(i)).name.equalsIgnoreCase(_nombre)){ //si ya existe ese topic
					//no hago nada
					break;
				}
				if(i==topicListExternos.size()-1){ //si estas en la ultima iteracion y no hay coincidencias, no existe
					topicListExternos.add(new TopicExterno(_nombre, _dirServidor));
					break;
				}
				
				
			}
		}else{
			topicListExternos.add(new TopicExterno(_nombre, _dirServidor));
			
		}
		
		
	}


	public void informarTopicTotal(String _dirServidor, Boolean _observable, String _nombre, String _protocolo, String _contenido){ //para topics tanto MQTT como CoAP

		//compruebo si el topic ya existe en la lista, si no existe lo creo
		if(!topicListTotal.isEmpty()){

			for(int i=0; i<=topicListTotal.size(); i++){

				if((topicListTotal.get(i)).name.equalsIgnoreCase(_nombre) ){ //si ya existe ese topic

					//actualizo sus datos
					topicListTotal.get(i).observable = _observable;
					topicListTotal.get(i).protocol = _protocolo;
					topicListTotal.get(i).contenido = _contenido;
					break;
				}
				if(i==topicListTotal.size()-1){ //si estas en la ultima iteracion y no hay coincidencias, no existe
					topicListTotal.add(new Topic(_nombre, _observable, _dirServidor, _protocolo, _contenido));
					break;
				}
				
				
			}
		}else{
			topicListTotal.add(new Topic(_nombre, _observable, _dirServidor, _protocolo, _contenido));
			
		}
		actualizarRootServidorHTTP();
		
	}
	
	


	
	public Topic comprobarTopic(String _nombreTopic){ //metodo para comprobar existencia de topic de la lista de topics de servidores coap externos

		if(!topicList.isEmpty()){
			for(int i=0; i<=topicList.size()-1; i++){
				if((topicList.get(i)).name.equalsIgnoreCase(_nombreTopic)){ //si ya existe ese topic, devuelvo el topic
					return topicList.get(i);
				}
			
			}
		}
		return null; 
	}

	public TopicExterno comprobarTopicExterno(String _nombreTopic){ //metodo para comprobar existencia de topic de la lista de topics de proxys extrenos

		if(!topicListExternos.isEmpty()){
			for(int i=0; i<=topicListExternos.size()-1; i++){

				if((topicListExternos.get(i)).name.equalsIgnoreCase(_nombreTopic)){ //si ya existe ese topic, devuelvo el topic
					return topicListExternos.get(i);
				}
			
			}
		}
		return null;
	}


	public Topic comprobarTopicTotal(String _nombreTopic){ //metodo para comprobar existencia de topic de la lista de topics tanto MQTT como CoAP

		if(!topicListTotal.isEmpty()){
			for(int i=0; i<=topicListTotal.size()-1; i++){
				if((topicListTotal.get(i)).name.equalsIgnoreCase(_nombreTopic)){ //si ya existe ese topic, devuelvo el topic
					return topicListTotal.get(i);
				}
			
			}
		}
		
		return null; 
	}


	public void informarUsuario(String _clienteId, Topic _tmpTopic, String _accion){

		if(_accion.equalsIgnoreCase("sub")){
			_tmpTopic.anadirUsuario(_clienteId);
		}else if(_accion.equalsIgnoreCase("unsub")){
			for(int i=0; i<=topicList.size()-1; i++){ 

				topicList.get(i).borrarUsuario(_clienteId);
			
			}
		}

	}	
	
	public void informarUsuarioTopicExterno(String _clienteId, TopicExterno _tmpTopicExterno, String _accion){

		if(_accion.equalsIgnoreCase("sub")){
			_tmpTopicExterno.anadirUsuario(_clienteId);
		}else if(_accion.equalsIgnoreCase("unsub")){
			for(int i=0; i<=topicListExternos.size()-1; i++){ 

				topicListExternos.get(i).borrarUsuario(_clienteId);
			
			}
		}

	}

}
