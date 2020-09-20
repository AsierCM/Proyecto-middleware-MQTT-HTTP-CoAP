package com.proyecto;

import java.lang.Thread;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.Semaphore;


public class GestorDescubridores extends Thread{
	public static ArrayList<String> listaServidores = new ArrayList<String>(); //Define las IP:puerto de los servidores de la red
	public PubSubBroker server;
	public GestorRecursos gestorRecursos;
	public Semaphore mutex;
	public Boolean sec;
	
	public GestorDescubridores(GestorRecursos _gestorRecursos, Semaphore _mutex, Boolean _sec){

		//lista de servidores CoAP a los que se les da servicio
		listaServidores.add("192.168.1.11:5683"); //1 unico servidor para probar

		gestorRecursos = _gestorRecursos;
		mutex = _mutex;
		sec=_sec;
	}
	
	public void run(){

		if(!listaServidores.isEmpty()){
		    for(int i=0;i<=listaServidores.size()-1; i++){ //Creo tantos threads como servidores haya en la red
			Descubridor tmpDescubridor = new Descubridor(listaServidores.get(i), gestorRecursos, mutex, sec);
			tmpDescubridor.start();
				
		    }
		}
	}	

}
