package com.proyecto;

import java.lang.Thread;
import java.io.*;

/**
 * Hello world!
 *
 */
public class App 
{

    private static String brokerIP = "IP:puerto"; //Ip del broker 
    private static String connectorChoice = "dtls";

    public static FileWriter fichero;
    public static PrintWriter pw;

    public static void main( String[] args )
    {


	if(args.length !=4){
	    System.out.println("ERROR: faltan nombre del archivo de resultados y el tipo de cliente");
	    System.exit(0);
	}

	Runtime.getRuntime().addShutdownHook(new Thread(){
	    public void run(){
		try{
		    fichero.close();
                    System.out.println("Shouting down ...");
		    Thread.sleep(1000);
		}catch(InterruptedException | IOException e){
		    e.printStackTrace();
		}
	    }
        });

        try{
	    String nombreArchivo = args[0];
	    String tipoCliente = args[1];
	    String topic = args[2];
	    Boolean conCoap = null;	

	    if(args[3].equals("con")){
                conCoap = true;
            }else if(args[3].equals("non")){
		conCoap = false;
	    }else{
	        System.out.println("ERROR: tipo de confirmacion desconocido");
	    }

            fichero = new FileWriter("src/main/output/"+nombreArchivo);
	    pw = new PrintWriter(fichero);
	
	    ClientePubSub cliente = new ClientePubSub(connectorChoice, pw, brokerIP, conCoap);

	    // Se definen los tipos de cliente: "sub", "pub", "get"
            switch(tipoCliente){
		
		case "sub":

		    cliente.subscribe(topic);

		    while(true){}
		    //break;

                case "pub":

		   
		    cliente.crearTopic(topic);
		    while(true){//Para tomar medidas
			cliente.publish(topic, String.valueOf(System.currentTimeMillis()) );
			Thread.sleep(2000);
                    }
                    //cliente.publish(topic);
                    //break;

                case "get":
		
		    while(true){ //Para tomar medidas
			cliente = new ClientePubSub(connectorChoice, pw, brokerIP, conCoap);
		        cliente.enviarGet(topic);
			
		        Thread.sleep(2000);
                    }

	            //break;
	
		default:
		    System.out.println("ERROR: tipo de cliente erroneo. Posibilidades: sub, pub, get.");
		    System.exit(0);
	            break;
	    }	
	}catch(Exception e){
		e.printStackTrace();
	}
		
	    
	
    }
}
