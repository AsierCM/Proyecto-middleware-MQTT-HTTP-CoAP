package com.proyecto;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.coap.CoAP.Type;


import java.lang.System;

public class SensorResource extends CoapResource {

	String nombre = null;
	String respuesta = null;

	public SensorResource(String _nombre, Boolean conCoAP) {

		// set resource identifier
		super(_nombre);
		nombre = _nombre;
		// set display name
		getAttributes().setTitle("Informacion sobre la "+_nombre);
		getAttributes().setObservable();
		// Seto observable option
		this.setObservable(true);

		if(conCoAP){
		    this.setObserveType(Type.CON);
		}else{
		    this.setObserveType(Type.NON);
		}

	}

	@Override
	public void handleGET(CoapExchange exchange) {

		// respond to the request
		exchange.respond(respuesta);
	}

	public void actualizar(){
		respuesta = null;
        switch(nombre){
 
            case "timestamp":
                respuesta = String.valueOf(System.currentTimeMillis());
                System.out.println("Actualizo timestamp: " + respuesta);

		}
		this.changed();
	}
}
