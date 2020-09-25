package com.proyecto;

import javax.net.ssl.HttpsURLConnection;
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
import java.io.InputStream;
import java.io.FileInputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.SecureRandom;
import javax.net.ssl.HostnameVerifier;

import java.io.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.KeyStoreException;
import java.security.KeyManagementException;
import java.security.cert.CertificateException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.SSLPeerUnverifiedException;

public class App 
{

    public static FileWriter fichero=null;
    public static PrintWriter pw=null;

    public static void main( String[] args ){
    
	
        String metodo = args[1];
        try{
           fichero = new FileWriter("src/main/output/"+args[0]);
           pw = new PrintWriter(fichero);
        }catch(Exception e){
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
        
        if(metodo.equalsIgnoreCase("get") || metodo.equalsIgnoreCase("GET")){
            while(true){

                try{
                    Thread.sleep(2000);
                    
                    long t1 = System.currentTimeMillis();
                    URL url = new URL("https://" + "IP:puerto" + "/ps/timestamp"); //poner IP:puerto correspondiente
                    HttpsURLConnection con = (HttpsURLConnection)url.openConnection();
                    
                    
                    SSLUtil sslUtil = new SSLUtil("password", "src/main/resources/config/clientkeystore.jks", "TLSv1.2", "SunX509");
                    SSLContext sc = sslUtil.getSSLContext();
                    con.setSSLSocketFactory(sc.getSocketFactory());			
            
                    con.setDefaultHostnameVerifier( new HostnameVerifier(){
                        public boolean verify(String hostname, SSLSession session){
                           return hostname.equals("IP"); //Poner IP del servidor

                        }
                    });

                    
                    con.connect();

                    System.out.println("Conexion con exito");

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
                    

                    //Calculo el retardo en milisegundos
                    long diff = Math.abs(t2 - t1);
                    System.out.println("Respuesta: " +response.toString());
                    System.out.println("Reatardo: " + String.valueOf(diff) + " ms");

                    try{
                        pw.println(String.valueOf(diff));
                    }catch(Exception e){
                    }

                }catch(MalformedURLException e){
                    e.printStackTrace();
                }catch(IOException e){	
                    e.printStackTrace();
                }catch(InterruptedException e){
                    e.printStackTrace();
                }
            }
        }else if(metodo.equalsIgnoreCase("put") || metodo.equalsIgnoreCase("PUT")){
            while(true){
                    try{
                URL url = new URL("https://155.210.158.29:9999/ps/timestamp");
                        HttpsURLConnection con = (HttpsURLConnection)url.openConnection();

                SSLUtil sslUtil = new SSLUtil("password", "src/main/resources/config/clientkeystore.jks", "TLSv1.2", "SunX509");
                        SSLContext sc = sslUtil.getSSLContext();
                        con.setSSLSocketFactory(sc.getSocketFactory());

                con.setDefaultHostnameVerifier( new HostnameVerifier(){
                    public boolean verify(String hostname, SSLSession session){
                                return hostname.equals("IP"); //Poner IP del servidor
                            }
                        });


                con.setDoOutput(true);
                        con.setRequestMethod("PUT");
                        con.connect();
                
                        OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());

                        long time = System.currentTimeMillis();
                out.write(String.valueOf(time));
                        out.close();
                        con.getInputStream();
                        Thread.sleep(2000);
                
                }catch(Exception e){
                e.printStackTrace();
                }
            }
        }

    }
}
	
