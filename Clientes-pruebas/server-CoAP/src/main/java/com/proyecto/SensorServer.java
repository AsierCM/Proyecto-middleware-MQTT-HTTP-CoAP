/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.proyecto;

import com.wiss.thom.elements.dtls.DtlsServerConnector;
import com.wiss.thom.elements.sctp.SctpServerConnector;
import com.wiss.thom.elements.ws.WsServerConnector;
import com.wiss.thom.elements.wss.WssServerConnector;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.UDPConnector;
import org.eclipse.californium.elements.tcp.TcpServerConnector;
import org.eclipse.californium.elements.tcp.TlsServerConnector;

import java.util.ArrayList;

import java.lang.System;

/**
 *
 * @author thomas
 */
public class SensorServer extends CoapServer {

    private static int COAP_PORT = NetworkConfig.getStandard().getInt(NetworkConfig.Keys.COAP_PORT);
    private final String connectorChoice;

    private ArrayList<SensorResource> resourceList = new ArrayList<SensorResource>();

    public SensorServer(String connectorChoice, Boolean conCoap) {
        this.connectorChoice = connectorChoice;
	if(connectorChoice.equals("udp")){
	    COAP_PORT=5683;
	}else if(connectorChoice.equals("dtls")){
	    COAP_PORT = 5684; //evitar problemas de usar el mismo puerto para pruebas
	}

	resourceList.add( new SensorResource("timestamp", conCoap));

	// Se añaden todos los recursos creados al server
	for(int i=0; i<resourceList.size();i++){
		this.add(resourceList.get(i));
	}
    }

    /**
     * Add individual endpoints listening on default CoAP port on all IPv4
     * addresses of all network interfaces.
     */
    public void addEndpoints() {
        for (InetAddress addr : EndpointManager.getEndpointManager().getNetworkInterfaces()) {
            
            if (addr instanceof Inet4Address) {
                InetSocketAddress bindToAddress = new InetSocketAddress(addr, COAP_PORT);
                
                NetworkConfig config = NetworkConfig.getStandard();

                Connector connector = null;
                switch (connectorChoice) {
                    case "udp":
                        connector = new UDPConnector(bindToAddress);
                        break;
                    case "dtls":
                        connector = new DtlsServerConnector(bindToAddress, "certificados/keyStore.jks", "certificados/trustStore.jks", 2000);
                        break;
                    default:
                        connector = new UDPConnector(bindToAddress);
                        break;
                }

                addEndpoint(new CoapEndpoint(connector, config));
                System.out.println("CoapServer, Added endpoint at addr:: " + addr.getHostAddress());
            }
        }
    }

    public void actualizarRecursos(){

        for(int i=0; i<resourceList.size();i++){
            resourceList.get(i).actualizar();
        }
    }
}
