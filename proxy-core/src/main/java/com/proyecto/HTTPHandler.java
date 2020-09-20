package com.proyecto;

import com.sun.net.httpserver.*;
import java.net.*;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import com.sun.net.httpserver.HttpExchange;


public class HTTPHandler implements HttpHandler {


	public String uri;
	public String contenido;
	public ProtocoloInteraccionHTTP protocoloInteraccionHttp;
	
	public HTTPHandler(String _uri, String _contenido, ProtocoloInteraccionHTTP _protocoloInteraccionHttp){
		uri = _uri;
		contenido = _contenido;
		protocoloInteraccionHttp = _protocoloInteraccionHttp;
	}

	public void handle(HttpExchange he) throws IOException {

		if(he.getRequestURI().toString().equalsIgnoreCase("/")){
			he.sendResponseHeaders(200, contenido.length());
			OutputStream os = he.getResponseBody();
			os.write(contenido.getBytes());
			os.close();
		}else{
			
			protocoloInteraccionHttp.iniciarComunicacion(null, null, null, null, null, he);

		}
		
		
	}
	
	public void actualizar(String _contenido){
		contenido = _contenido;
	}

	




}
