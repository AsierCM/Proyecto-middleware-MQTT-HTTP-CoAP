package com.proyecto;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.net.URI;
import java.net.URISyntaxException;


public class Suscriptor extends Thread implements MqttCallback  {

    private final int qos = 0;
    private String topic;
    private MqttClient client;
	
    private MqttClient client2 = new MqttClient("tcp://127.0.0.1:7777", "ClienteSub",new MemoryPersistence());
    private boolean salir =false;


    public Suscriptor(String nombre, String dirProxy) throws MqttException {

	topic = nombre;

	String host = "tcp://"+dirProxy;

        String clientId = "Proxy-1";

        MqttConnectOptions conOpt = new MqttConnectOptions();
        conOpt.setCleanSession(true);

        this.client = new MqttClient(host, clientId, new MemoryPersistence());
        this.client.setCallback(this);
        this.client.connect(conOpt);

        
    }

    public void run(){	
	try{

	    this.client.subscribe(this.topic, qos);
	
	    while(!salir){
	        Thread.sleep(200);
	    }
	    
	    client.unsubscribe(this.topic);

	    client.disconnect();

	}catch(MqttException | InterruptedException me){
	    
	    System.out.println(me.getMessage());
	    System.out.println(me.getLocalizedMessage());
	    System.out.println(me.getCause());
	    System.out.println(me);
            me.printStackTrace();
	    System.out.println(me.toString());
	}
    }

    public void sendMessage(String payload) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(qos);
        this.client.publish(this.topic, message); // Blocking publish
    }

    /**
     * @see MqttCallback#connectionLost(Throwable)
     */
    public void connectionLost(Throwable cause) {

    }

    /**
     * @see MqttCallback#deliveryComplete(IMqttDeliveryToken)
     */
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    /**
     * @see MqttCallback#messageArrived(String, MqttMessage)
     */
    public void messageArrived(String topic, MqttMessage message)  {
	
	try{	
	    String contenido= new String(message.getPayload());

	    client2.connect();

	    client2.publish(topic,message);
	    client2.disconnect();
	             
    	}catch(MqttException me){
	    
	    System.out.println(me.getMessage());
	    System.out.println(me.getLocalizedMessage());
	    System.out.println(me.getCause());
	    System.out.println(me);
            me.printStackTrace();
	    System.out.println(me.toString());
	}
    }

    public void parar(){
	salir=true;

    }

}

