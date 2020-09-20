package com.proyecto;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;

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

import java.lang.Thread;

public class ProtocoloInteraccionMqtt implements ProtocoloInteraccion{

	private GestorRecursos gestorRecursos;
	private Semaphore mutex;
	private Boolean sec;

	public ProtocoloInteraccionMqtt(GestorRecursos _gestorRecursos, Semaphore _mutex, Boolean _sec){
	
		gestorRecursos = _gestorRecursos;
		mutex = _mutex;
		sec = _sec;

	}

	public Boolean iniciarComunicacion(InterceptPublishMessage pubMsg, InterceptSubscribeMessage subMsg, CoapExchange coapExchange, Exchange exchange , String tipoAccion, HttpExchange he){

		if(pubMsg != null && subMsg == null){ // gestiono un PUBLISH

			String name = pubMsg.getTopicName();
			String content = new String(pubMsg.getPayload().array());			 

			if(!pubMsg.getClientID().equalsIgnoreCase("Broker")){
				//notifico la publicacion al gestor
		
				try{

				    mutex.acquire();
			   	    propagarProxys(pubMsg.getClientID(), name, content);
					
    				    mutex.acquire();
					
				    sendPut(name,content);

				    

										

				}catch(InterruptedException e){
				}
						
			}
			

		} else if( pubMsg == null && subMsg != null){ // gestiono un SUBSCRIBE


			String clienteId = subMsg.getClientID();
			String nombreTopic = subMsg.getTopicFilter();

			try{
				mutex.acquire();
				Topic tmpTopic =  gestorRecursos.comprobarTopic(nombreTopic); 
				if(tmpTopic != null){ //sera true si el topic es de un server CoAP externo
					gestorRecursos.informarUsuario(clienteId, tmpTopic, "sub"); //añado un usuario a ese topic para registrarlo
				
					if(tmpTopic.observador == null){ //significa que no hay un observador pidiendo la informacion del topic

					
						Observador tmpObservador = new Observador(tmpTopic.name, tmpTopic.source, tmpTopic.observable, gestorRecursos, "observar", null, null, sec);
						tmpTopic.setObservador(tmpObservador);
						tmpObservador.start();
					
					}


				}else{
				    TopicExterno tmpTopicExterno = gestorRecursos.comprobarTopicExterno(nombreTopic);
				    if(tmpTopicExterno!=null){ //sera true si es de un proxy retransmitido
					
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
					        Suscriptor sub = new Suscriptor(nombreTopic, dirIp);
					        tmpTopicExterno.setSuscriptor(sub);
					        sub.start();
					    }catch(MqttException e){
					    }
					    
					}
					//Informar del nuevo usuario suscrito
					gestorRecursos.informarUsuarioTopicExterno(clienteId, tmpTopicExterno, "sub");
					
					
				    }
				}
					
				mutex.release();
			}catch(InterruptedException e){
			}

		}
		return true;

	}

	public void cancelarComunicacion(String clientId){
	
		try{

			mutex.acquire();		
			gestorRecursos.informarUsuario(clientId, null, "unsub");
			gestorRecursos.informarUsuarioTopicExterno(clientId,null,"unsub");
			mutex.release();
		}catch(InterruptedException e){
		}
	}

	public void sendPut(String _nombre, String _contenido){

		// Metodo para convertir un PUB MQTT en un PUT CoAP
		
		String connectorChoice = "udp"; 
		ClientePubSub cliente = new ClientePubSub(connectorChoice, "127.0.0.1");
		String BASE_URI = "coap://127.0.0.1:5683/ps/";
		String path = BASE_URI;		

		String[] parts = _nombre.split("/");

		if(gestorRecursos.comprobarTopicTotal(_nombre)==null){ //en caso de que no exista, tengo que crearlo en el broker coap para evitar que se "machaquen"
			
			if(parts.length > 1){
			    // Primero creo de uno en uno desde el raiz
			    if(gestorRecursos.comprobarTopicTotal(parts[0])==null){
			        cliente.crearTopic(parts[0], MediaTypeRegistry.APPLICATION_LINK_FORMAT);
			        gestorRecursos.informarTopicTotal(null, false, parts[0], "mqtt",null);
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
						cliente.crearTopic(parts[i], MediaTypeRegistry.APPLICATION_LINK_FORMAT);
						gestorRecursos.informarTopicTotal(null, false, path+parts[i], "mqtt",null);
					}

				}else{

				        cliente.crearTopic(parts[i], MediaTypeRegistry.TEXT_PLAIN);
					gestorRecursos.informarTopicTotal(null, true, path+parts[i], "mqtt", null); //actualiza el protocolo de procedencia del topic
					cliente.setUri(path+parts[i]);
				}
				
				
			    }
			}
				
				
		}
	
		CoapResponse response = null;

		gestorRecursos.informarTopicTotal(null, true, _nombre, "mqtt", _contenido); //actualiza el protocolo de procedencia del topic

		mutex.release();
		cliente.publish(_nombre, _contenido);		

	}

	public void propagarProxys(String _idTransmisor, String _name, String _content) {

		
			
	        ArrayList listaProxys = gestorRecursos.getListaProxys();
		ArrayList<String> listaProxysAux = new ArrayList<String>();

		if(gestorRecursos.comprobarTopicExterno(_name)==null){ //si entra aqui es porque no existe y es la primera vez que se recibe y existen proxys conectados

		    //Veo si me lo ha enviado un poxy o un cliente cualquiera

		    Boolean recProxy = false; //indica si se ha recibido de otro proxy o no
		    String idProxySource = null;
		    if(!listaProxys.isEmpty()){
			    for(int i=0; i<=listaProxys.size()-1;i++){ 
			        String idProxy = ((String)listaProxys.get(i)).split(" ")[0];
			        if(_idTransmisor.equalsIgnoreCase(idProxy)){
				    recProxy = true;
				    idProxySource = idProxy;						
			        }else{
				    listaProxysAux.add(idProxy);
				}
                            }

		    }

		    if(recProxy){ //si lo he recibido de un proxy se lo reenvio al resto de proxys
			
			for(int i=0; i<=listaProxysAux.size()-1;i++){
		
			    String broker = ((String)listaProxysAux.get(i)).split(" ")[1];
		
			    try{
			  	    MqttClient client = new MqttClient("tcp://" + broker, "Proxy-1",new MemoryPersistence());
				    client.connect();
				    MqttMessage message = new MqttMessage(_content.getBytes());
				    message.setQos(0);
				    client.publish(_name,message);
				    client.disconnect();
			    }catch(MqttException me){
				    System.out.println(me.getReasonCode());
				    System.out.println(me.getMessage());
				    System.out.println(me.getLocalizedMessage());
				    System.out.println(me.getCause());
				    System.out.println(me);
			    }
			}
			gestorRecursos.informarTopicExterno( idProxySource, _name); 

		    }else{ //si no lo he recibido de un proxy, lo he recibido de un cliente cualquiera

			if(gestorRecursos.comprobarTopicTotal(_name)==null){ //si no existe entrara aqui

			    for(int i=0; i<=listaProxys.size()-1;i++){
		
			        String broker = ((String)listaProxys.get(i)).split(" ")[1];
			        try{
				    //usar el ID del proxy correspondiente
			  	    MqttClient client = new MqttClient("tcp://" + broker, "Proxy-1",new MemoryPersistence());
				    client.connect();
				    MqttMessage message = new MqttMessage(_content.getBytes());
				    message.setQos(0);
				    client.publish(_name,message);
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
		}
		mutex.release();

	}
		

	

}
			

			

		
