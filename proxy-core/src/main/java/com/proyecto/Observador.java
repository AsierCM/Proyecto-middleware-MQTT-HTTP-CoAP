package com.proyecto;

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
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.Utils;
//import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.californium.core.network.config.NetworkConfig;
//import org.eclipse.californium.core.network.config.NetworkConfigDefaultHandler;
import org.eclipse.californium.core.network.config.NetworkConfig.Keys;

import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Response;

import com.sun.net.httpserver.HttpExchange;

import java.io.OutputStream;
import java.io.IOException;

import java.util.concurrent.Semaphore;

public class Observador extends Thread{
	public String nombre;	
	public String dirServidor;
	public Boolean observable;
	public GestorRecursos gestorRecursos;
	public Boolean exit = false;
	public String tipo;
	public CoapClient client = null;
	public ClienteSubObservador cliente;	
	public CoapObserveRelation relation = null;
	public Exchange exchange =null;
	public HttpExchange he = null;
	public Boolean sec;

	
	public Observador(String _nombre, String _dirServidor, Boolean _observable, GestorRecursos _gestorRecursos, String _tipo, Exchange _exchange, HttpExchange _he, Boolean _sec){
		
		nombre=_nombre;
		dirServidor = _dirServidor;
		observable = _observable;
		gestorRecursos = _gestorRecursos;
		tipo = _tipo;
		exchange = _exchange;
		he = _he;
		sec = _sec;


	}
	public void run(){
		String connectorChoice = "udp";
		if(sec){
		        connectorChoice = "dtls";
		}
		
	
		if(observable && tipo.equalsIgnoreCase("observar")){

			
			cliente = new ClienteSubObservador(connectorChoice, dirServidor);
			cliente.subscribe(nombre);
			
			while(!exit){
				
			}
			
		}else if(!observable && tipo.equalsIgnoreCase("observar")){
		
			cliente = new ClienteSubObservador(connectorChoice, dirServidor); //cliente que pide la informacion
			ClienteSubObservador cliente2 = new ClienteSubObservador("udp" , "127.0.0.1"); //cliente que retransmite
			
			while(!exit){
				//periodicamente hago un GET --> simulo una observacion
	
				CoapResponse response = cliente.enviarGet(nombre);

				cliente2.publish("ps/"+nombre, response.getResponseText());
				
				try{
					TimeUnit.SECONDS.sleep(5);
				}catch(InterruptedException e){}
			}
				
				
		}else if(tipo.equalsIgnoreCase("get")){
	
			cliente = new ClienteSubObservador(connectorChoice, dirServidor); //cliente que pide la informacion
			CoapResponse response = cliente.enviarGet(nombre);

			System.out.println(response.getResponseText());

	
			if (exchange != null && he == null) { // ORIGEN COAP
			        System.out.println("Entro en COAP");
				exchange.sendResponse(response.advanced());

			}else if(exchange == null && he != null) { // ORIGEN HTTP
				try{

					String contenido = response.getResponseText();
					he.sendResponseHeaders(200, contenido.length());
					OutputStream os = he.getResponseBody();
					os.write(contenido.getBytes());
					os.close();
				}catch(IOException e){
				    e.printStackTrace();		
				}

			}


			cliente.desconectar();
		}
				
			
			
	}
	public void parar(){

		if(observable && tipo.equalsIgnoreCase("observar")){

			cliente.desconectar();

		}
		
		exit = true;
	}
}
