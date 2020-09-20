package com.proyecto;

import  java.util.List;
import java.util.ArrayList;

import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.coap.CoAP.Code;

import io.moquette.interception.messages.InterceptPublishMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.interception.messages.InterceptConnectionLostMessage;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.sun.net.httpserver.HttpExchange;

import java.util.concurrent.Semaphore;
import java.lang.InterruptedException;

public class ProtocoloInteraccionCoap implements ProtocoloInteraccion {

	private GestorRecursos gestorRecursos;
	private Semaphore mutex;
	private Boolean sec;

	public ProtocoloInteraccionCoap(GestorRecursos _gestorRecursos, Semaphore _mutex, Boolean _sec){

		gestorRecursos = _gestorRecursos;
		mutex = _mutex;
		sec = _sec;
	}


	public Boolean iniciarComunicacion(InterceptPublishMessage pubMsg, InterceptSubscribeMessage subMsg, CoapExchange coapExchange,Exchange exchange, String tipoAccion, HttpExchange he){

		// SOLO NECESITAS PASAR EL EXCHANGE Y TIENES LOS DEMAS VALORES
		
		String codigo = null;
		Request request = null;

		
		String nombre;
		byte[] contenido;
		String contenidoStr;
		String dirServidor = null;
		Boolean observable = true;
		Topic tmpTopic = null;
		String exchangeURI = null;

		if(coapExchange != null){
			request = coapExchange.advanced().getRequest();
		}else if(exchange != null){
			request = exchange.getRequest();
			
		}

			
		if(request.getCode() == Code.GET){
			codigo = "get";
		}else if(request.getCode() == Code.PUT){
			codigo = "put";
		}else if(request.getCode() == Code.POST){
			codigo = "post";
		}
	
		Boolean salida = true;

		switch(codigo){

			case "get": // Si el request es un GET
				
				request = exchange.getRequest();
      				String originalURIPath = request.getOptions().getUriPathString().substring(3);

				try{

					mutex.acquire();
					if(originalURIPath.length()>3){
		   				tmpTopic = gestorRecursos.comprobarTopic(originalURIPath);
		   	 			if(tmpTopic != null && request.getCode() == Code.GET){
		
							if(request.getOptions().getObserve() != null){ //Observacion	
								if(request.getOptions().getObserve() == 0){
								
									gestorRecursos.informarUsuario(request.getSource()+":"+request.getSourcePort(), tmpTopic, "sub"); //añado un usuario a ese topic para registrarlo

									if(tmpTopic.observador == null){ //significa que ya hay un observador pidiendo la informacion del topic
									
										Observador tmpObservador = new Observador(tmpTopic.name, tmpTopic.source, tmpTopic.observable, gestorRecursos, "observar", exchange, null, sec);
										tmpTopic.setObservador(tmpObservador);
										tmpObservador.start();
									
									}
								}
							}else{
							
								Observador tmpObservador = new Observador(tmpTopic.name, tmpTopic.source, tmpTopic.observable, gestorRecursos, "get", exchange, null, sec);
								tmpObservador.start();

								salida = false;
			
							}
		    				}else{
						   TopicExterno tmpTopicExterno = gestorRecursos.comprobarTopicExterno(originalURIPath);
						    if(tmpTopicExterno!=null){ //sera true si es de un proxy retransmitido

							if(request.getOptions().getObserve() != null){ //Observacion
							    // Si no existe Suscriptor, crearlo y añadirlo al topic
							    if(tmpTopicExterno.clienteSuscriptor == null){
							        try{
							   
								    ArrayList proxys = gestorRecursos.getListaProxys();
								    String  dirIp = null;
							   
								    for(int i=0;i<=proxys.size()-1;i++){
								        if(((String)proxys.get(i)).split(" ")[0].equalsIgnoreCase(tmpTopicExterno.idProxySource)){
									    dirIp = ((String)proxys.get(i)).split(" ")[1];
									    break;
								        }
								    }
								    Suscriptor sub = new Suscriptor(tmpTopicExterno.name, dirIp);
								    tmpTopicExterno.setSuscriptor(sub);
								    sub.start();
							        }catch(MqttException e){
							        }
							    
							    }
							    //Informar del nuevo usuario suscrito
							    gestorRecursos.informarUsuarioTopicExterno(request.getSource()+":"+request.getSourcePort(), tmpTopicExterno, "sub");
							}else{//Get dirigido a un recurso de un Proxy externo

							    ArrayList proxys = gestorRecursos.getListaProxys();
							    String  dirIp = null;
						   
							    for(int i=0;i<=proxys.size()-1;i++){
							        if(((String)proxys.get(i)).split(" ")[0].equalsIgnoreCase(tmpTopicExterno.idProxySource)){
								    dirIp = ((String)proxys.get(i)).split(" ")[1];
								    break;
							        }
							    }
							    String dirIp2 = dirIp.split(":")[0]+":5683";
							    Observador tmpObservador = new Observador("ps/"+tmpTopicExterno.name, dirIp2, true, gestorRecursos, "get", exchange, null, sec);
							    tmpObservador.start();

							    salida = false;								
							

							}
						    }
						}


		   			}
					mutex.release();
					break;

				}catch(InterruptedException e){
				}
				
			
			case "put": // Si el request es un PUT
	
				exchangeURI = "/" + coapExchange.getRequestOptions().getUriPathString();
				if(exchangeURI.equalsIgnoreCase("/ps")){
					break;
				}
				nombre = exchangeURI.substring(4);
				contenido = coapExchange.getRequestPayload();

				contenidoStr = new String(contenido);
				dirServidor= null; // no hace falta, solo se necesita para recursos de Servidores Coap no PubSub
				observable =true; // significa que si es observable
				
				try{
					mutex.acquire();		
					
					tmpTopic = gestorRecursos.comprobarTopicTotal(nombre);
					if(tmpTopic != null){
						if(tmpTopic.protocol.equalsIgnoreCase("mqtt")){
							gestorRecursos.informarTopicTotal(tmpTopic.source, tmpTopic.observable, nombre, "coap", contenidoStr);
						}else if(tmpTopic.protocol.equalsIgnoreCase("coap")){

							
							gestorRecursos.informarTopicTotal(tmpTopic.source, tmpTopic.observable, nombre, "coap", contenidoStr);
							sendPub(nombre, contenido);
						}
					}else{ // aun no existe ese topic
						propagarProxys(nombre, contenidoStr);
						if(observable){
							gestorRecursos.informarTopicTotal(dirServidor, true, nombre, "coap", contenidoStr);
						}else{
							gestorRecursos.informarTopicTotal(dirServidor, false, nombre, "coap", contenidoStr);
						}
						sendPub(nombre, contenido);
		
					}
					mutex.release();
					break;
				}catch(InterruptedException e){
				}

			case "post":
			
				exchangeURI = "/" + coapExchange.getRequestOptions().getUriPathString();

				if(exchangeURI.equalsIgnoreCase("/ps")){
					break;
				}
				nombre = exchangeURI.substring(3);
				contenido = coapExchange.getRequestPayload();
				
				contenidoStr = new String(contenido);
				dirServidor= null; // no hace falta, solo se necesita para recursos de Servidores Coap no PubSub
				observable = true; // significa que si es observable

				try{
					mutex.acquire();
					tmpTopic = gestorRecursos.comprobarTopicTotal(nombre);
					if(tmpTopic != null){
						if(tmpTopic.protocol.equalsIgnoreCase("mqtt")){
							gestorRecursos.informarTopicTotal(tmpTopic.source, tmpTopic.observable, nombre, "coap", contenidoStr);
						}else if(tmpTopic.protocol.equalsIgnoreCase("coap")){
							gestorRecursos.informarTopicTotal(tmpTopic.source, tmpTopic.observable, nombre, "coap", contenidoStr);
							sendPub(nombre, contenido);
						}
					}else{ // aun no existe ese topic
						if(observable){
							gestorRecursos.informarTopicTotal(dirServidor, true, nombre, "coap", contenidoStr);
						}else{
							gestorRecursos.informarTopicTotal(dirServidor, false, nombre, "coap", contenidoStr);
						}
						//para MQTT
						sendPub(nombre, contenido);

					
		
					}
					mutex.release();
					break;
				}catch(InterruptedException e){
				}

			default:

		}
		return salida;
	}

	public void cancelarComunicacion(String clientId){
		try{
			mutex.acquire();
			gestorRecursos.informarUsuario(clientId, null, "unsub");
			gestorRecursos.informarUsuarioTopicExterno(clientId, null, "unsub");
			mutex.release();
		}catch(InterruptedException e){
		}
	}

	public void sendPub(String _nombre, byte[] _contenido){
		
		Topic tmpTopic=gestorRecursos.comprobarTopicTotal(_nombre);
		if(tmpTopic!=null){ // para topics tipo PUB/SUB
			if(tmpTopic.protocol.equalsIgnoreCase("coap")){ //solo lo publico en el broker si me lo han enviado desde coap
				try{
					MqttClient client = new MqttClient("tcp://127.0.0.1:7777", "Broker",new MemoryPersistence());
					client.connect();
					MqttMessage message = new MqttMessage(_contenido);
					message.setQos(1);
					client.publish(_nombre,message);
					client.disconnect();
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

	public void propagarProxys(String _nombre, String _contenido){
	    ArrayList listaProxys = gestorRecursos.getListaProxys();

	    if(gestorRecursos.comprobarTopicTotal(_nombre)==null){ //si entra aqui es porque no existe y es la primera vez que se recibe
		for(int i=0; i<=listaProxys.size()-1;i++){
		
		    String broker = ((String)listaProxys.get(i)).split(" ")[1];
	
		    try{
			    //usar el ID del proxy correspondiente
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
	

	
