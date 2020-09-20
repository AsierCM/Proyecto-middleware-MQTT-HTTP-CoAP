package com.proyecto;

import java.util.ArrayList;
import java.util.List;

public class Topic{

	public String name; //name of topic
	public String protocol; //protocol of source topic: "mqtt" or "coap"
	public String source; // Server IP in coap source case
	public Boolean observable;
	public ArrayList<String> usuariosSub;
	public Observador observador;
	public String contenido;

	public Topic(String _name, Boolean _observable, String _source, String _protocol, String _contenido ){
	    name = _name;
	    observable = _observable;
	    protocol = _protocol;
	    source = _source;
	    usuariosSub = new ArrayList<String>();
	    contenido = _contenido;
		
		
	}
	
	public void setObservador(Observador _observador){
	    observador = _observador;
	}

	public void anadirUsuario(String _nombre){	
	    usuariosSub.add(_nombre);
	}

	public void borrarUsuario(String _nombre){ 
	    if(!usuariosSub.isEmpty()){
		for(int i=0; i<=usuariosSub.size()-1; i++){ //busco el usuario
		    if((usuariosSub.get(i)).equalsIgnoreCase(_nombre)){
			usuariosSub.remove(i);
			i--;
		    }
		
		}
		if(usuariosSub.isEmpty()){
		    observador.parar();
		    observador = null;
		}
	    }
	}
	

}
