package com.proyecto;

import java.net.HttpURLConnection;
import java.net.URL;

import java.lang.System;
import java.lang.Thread;

import java.lang.StringBuffer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.lang.InterruptedException;
import java.net.ProtocolException;

import java.io.*;


public class App 
{

    public static void main( String[] args )
    {    

	//String metodo = args[1];
        try{
           
            String metodo = args[1]; 
                final FileWriter fichero = new FileWriter("src/main/output/"+args[0]);
                PrintWriter pw = new PrintWriter(fichero);
        
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
            if(metodo.equalsIgnoreCase("get") || metodo.equalsIgnoreCase("GET")){
                while(true){
        
            
                    long t1 = System.currentTimeMillis();

                    URL url = new URL("http://"+ "IP:puerto" + "/ps/timestamp"); //poner IP:puerto del servidor 
                    HttpURLConnection con = (HttpURLConnection)url.openConnection();

                    
                    con.setRequestMethod("GET");
            
                    InputStreamReader isr = new InputStreamReader(con.getInputStream());
                    BufferedReader in = new BufferedReader(isr);

                    String inputLine;
                    StringBuffer response = new StringBuffer();

                    while((inputLine = in.readLine()) != null){
                        response.append(inputLine);
                    }
                    
                    long t2 = System.currentTimeMillis();

                    con.disconnect();
                    isr.close();
                    in.close();
                    
                

                    //long t1 = Long.parseLong(response.toString());

                    //Calculo el retardo en milisegundos
                    long diff = Math.abs(t2 - t1);
                    System.out.println("Respuesta: " +response.toString());
                    System.out.println("Reatardo: " + String.valueOf(diff) + " ms");
                    
                    try{	
                        pw.println(String.valueOf(diff));
                    }catch( Exception e){
                    }
                
            
                
                
                    Thread.sleep(2000);
                }
            }else if(metodo.equalsIgnoreCase("put") || metodo.equalsIgnoreCase("PUT")){
            
                while(true){
                    URL url = new URL("IP:puerto" + "/ps/timestamp"); //poner IP y puerto correspondiente
                    HttpURLConnection con =(HttpURLConnection)url.openConnection();
                   
                        con.setDoOutput(true);
                        con.setRequestMethod("PUT");
                         
                        OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());

                        long time = System.currentTimeMillis();
                        out.write(String.valueOf(time));
                        out.flush();
                    con.getInputStream(); 
                    System.out.println("PUT: " + String.valueOf(time)); 
                    Thread.sleep(2000);
                }
            }	    

        }catch(MalformedURLException e){
        }catch(IOException e){
            e.printStackTrace();
        }catch(InterruptedException e){
            e.printStackTrace();
        }
	
    }
}
	
