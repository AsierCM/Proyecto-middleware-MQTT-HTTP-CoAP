package com.proyecto;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttClient;

public class TopicExterno{

	public String name; //name of topic
	public String idProxySource; 
	public ArrayList<String> usuariosSub = new ArrayList<String>();;
	public Suscriptor clienteSuscriptor;

	
	public TopicExterno(String _name, String _idProxySource){

	    name = _name;
	    idProxySource = _idProxySource;
	
	}

	public void setSuscriptor(Suscriptor _sub){
	    clienteSuscriptor = _sub;
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
			
		    clienteSuscriptor.parar();
		    clienteSuscriptor = null;
		}
	    }
	}

}
