/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.proyecto;

import java.util.logging.Level;
import org.eclipse.californium.core.CaliforniumLogger;

import java.lang.InterruptedException;
import org.eclipse.californium.core.coap.CoAP.Type;

/**
 *
 * @author thomas
 */
public class Main {

    public static void main(String[] args) {
        CaliforniumLogger.initialize();
        CaliforniumLogger.setLevel(Level.WARNING);  // ----- Change to appropriate level! -----
        
        String connectorChoice = "udp";

        Boolean conCoap = null;
	
        if(args.length == 0){
            System.out.println("ERROR: falta tipo con/non");
            System.exit(0);
            }else{
            if(args[0].equals("con")){
            conCoap = true;
            }else{
            if(args[0].equals("non")){
                conCoap = false;
                }else{
                System.out.println("Tipo de confirmacion desconocida: con/non");
                System.exit(0);
                }
            }
        }
        
        SensorServer server = new SensorServer(connectorChoice, conCoap);
        server.addEndpoints();
        server.start();


	// Actualizo el valor de los recursos cada 1s
        while(true){
            try{
                Thread.sleep(2000);
                server.actualizarRecursos();
            }catch(InterruptedException e){
            
            }
        }
    }

}
