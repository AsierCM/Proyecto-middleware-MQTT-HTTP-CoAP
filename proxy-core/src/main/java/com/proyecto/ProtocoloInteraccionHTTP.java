package com.proyecto;

import  java.util.List;
import java.util.ArrayList;

import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.coap.CoAP.Code;
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

import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.lang.StringBuilder;

import java.util.concurrent.Semaphore;
import java.lang.InterruptedException;


public class ProtocoloInteraccionHTTP implements ProtocoloInteraccion {

	private GestorRecursos gestorRecursos;
	private Semaphore mutex;
	private Boolean sec;

	public ProtocoloInteraccionHTTP(GestorRecursos _gestorRecursos, Semaphore _mutex, Boolean _sec){

		gestorRecursos = _gestorRecursos;
		mutex = _mutex;
		sec = _sec;
	}


	public Boolean iniciarComunicacion(InterceptPublishMessage pubMsg, InterceptSubscribeMessage subMsg, CoapExchange coapExchange,Exchange exchange, String tipoAccion, HttpExchange he){

		String metodo = he.getRequestMethod();
		String nombre = he.getRequestURI().toString();
			
		switch(metodo){
	
			case "get":
			case "GET":
	
				try{
					mutex.acquire();
					Topic tmpTopic = gestorRecursos.comprobarTopic(nombre.substring(4));
					if(tmpTopic != null){
						Observador tmpObservador = new Observador(tmpTopic.name, tmpTopic.source, tmpTopic.observable, gestorRecursos, "get", exchange, he, sec);
						tmpObservador.start();
					
					}else{

					    TopicExterno tmpTopicExterno = gestorRecursos.comprobarTopicExterno(nombre.substring(4));
					    if(tmpTopicExterno!=null){ //sera true si es de un proxy retransmitido

						ArrayList proxys = gestorRecursos.getListaProxys();
					        String  dirIp = null;
				   
					        for(int i=0;i<=proxys.size()-1;i++){
					            if(((String)proxys.get(i)).split(" ")[0].equalsIgnoreCase(tmpTopicExterno.idProxySource)){
						        dirIp = ((String)proxys.get(i)).split(" ")[1];
						        break;
					            }
					        }
					        String dirIp2 = dirIp.split(":")[0]+":5683";
					        Observador tmpObservador = new Observador("ps/"+tmpTopicExterno.name, dirIp2, true, gestorRecursos, "get", exchange, he, sec);
					        tmpObservador.start();

					    }else{

						tmpTopic = gestorRecursos.comprobarTopicTotal(nombre.substring(4));
						if(tmpTopic != null){
							//significa que es un topic tipo PubSub
							try{
								he.sendResponseHeaders(200, (tmpTopic.contenido).length());
								OutputStream os = he.getResponseBody();
								os.write((tmpTopic.contenido).getBytes());
								os.close();
							}catch(IOException e){
						
							}
						}else{
							//significa que no existe y devuelve un 404

							try{
		
								he.sendResponseHeaders(404, "Not Found".length());
								OutputStream os = he.getResponseBody();
								os.write("Not Found".getBytes());
								os.close();
							}catch(IOException e){
						
							}
						}
					    }
					
					}
					mutex.release();
					break;
				}catch(InterruptedException e){
				}

				case "PUT":
				case "put":
				case "POST":
				case "post":
					
					try{
	
						InputStream is = he.getRequestBody();
						InputStreamReader isr = new InputStreamReader(is);

						BufferedReader br = new BufferedReader(isr);
						int c;
						StringBuilder buf = new StringBuilder(512);
						while((c = br.read()) != -1){
							buf.append((char) c);
						}
						br.close();
						isr.close();
					
						String contenido = buf.toString();

						mutex.acquire();
			   	   		propagarProxys(nombre.substring(4), contenido);

						mutex.acquire();
						gestorRecursos.informarTopicTotal(null, true, nombre.substring(4), "http", contenido); //actualiza el protocolo de procedencia del topic	
  						mutex.release();
						sendPut(nombre.substring(4), contenido);

						he.sendResponseHeaders(200, "Resource updated succesfully".length());
						OutputStream os = he.getResponseBody();
						os.write("Resource updated succesfully".getBytes());
						os.close();
						
					}catch(IOException | InterruptedException e){
					}

					
		}
		return true;

	}

	public void sendPut(String _nombre, String _contenido){
		// Metodo para publicar el PUT en el Broker CoAP y en el broker MQTT

		String uri = new String("coap://127.0.0.1/ps/"+_nombre);

		CoapClient client1 = new CoapClient(uri);
		client1.useCONs();
		CoapResponse response = null;

		client1.put(_contenido, MediaTypeRegistry.TEXT_PLAIN);

	        try{
                    MqttClient client = new MqttClient("tcp://127.0.0.1:7777", "Broker",new MemoryPersistence());
                    client.connect();
                    MqttMessage message = new MqttMessage(_contenido.getBytes());
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


	public void cancelarComunicacion(String clientId){

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
	    mutex.release();
	}
					

		
}
	

	
