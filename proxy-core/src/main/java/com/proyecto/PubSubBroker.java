/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.proyecto;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.eclipse.californium.core.CoapBroker;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.network.config.NetworkConfig;

import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.core.server.BrokerInterface;
import org.eclipse.californium.core.PubSubTopic;

import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.UDPConnector;
import org.eclipse.californium.elements.tcp.TcpServerConnector;
import org.eclipse.californium.elements.tcp.TlsServerConnector;

//Estan dentro del paquete element-conector de Thom Wiss 1.1.0-SNAPSHOT
import com.wiss.thom.elements.dtls.DtlsServerConnector;
import com.wiss.thom.elements.sctp.SctpServerConnector;
import com.wiss.thom.elements.ws.WsServerConnector;
import com.wiss.thom.elements.wss.WssServerConnector;

import com.proyecto.ProtocoloInteraccionCoap;

/**
 *
 * @author thomas
 */
public class PubSubBroker extends CoapBroker {

    //private static final int COAP_PORT = NetworkConfig.getStandard().getInt(NetworkConfig.Keys.COAP_PORT);
    private int[] COAP_PORT;
    private String[] connectorChoice;

    public GestorDescubridores gestorDescubridores;
    public ProtocoloInteraccionCoap protocoloInteraccionCoap;

    public PubSubBroker(String[] connectorChoice, int[] coap_port) {
        this.connectorChoice = connectorChoice;
	this.COAP_PORT = coap_port;
    }

    /**
     * Add individual endpoints listening on default CoAP port on all IPv4
     * addresses of all network interfaces.
     */
    public void addEndpoints() {
	for(int i = 0; i<connectorChoice.length; i++){
		for (InetAddress addr : EndpointManager.getEndpointManager().getNetworkInterfaces()) {
		    // only binds to IPv4 addresses and localhost
		    /*
		    if (addr instanceof Inet4Address || addr instanceof Inet6Address || addr.isLoopbackAddress()) {
		        InetSocketAddress bindToAddress = new InetSocketAddress(addr, COAP_PORT);
		        addEndpoint(new CoapEndpoint(bindToAddress));
		        System.out.println("PubSubBroker, Added endpoint at addr:: " + addr.getHostAddress());
		    }
		     */
		    if (addr instanceof Inet4Address) {
		        InetSocketAddress bindToAddress = new InetSocketAddress(addr, COAP_PORT[i]);
		        
		        NetworkConfig config = NetworkConfig.getStandard();

		        Connector connector = null;
		        switch (connectorChoice[i]) {
		            case "udp":
		                connector = new UDPConnector(bindToAddress);
		                break;
		            case "tcp":
		                connector = new TcpServerConnector(bindToAddress, 2, 1000);
		                break;
		            case "ws":
		                connector = new WsServerConnector(bindToAddress, 100);
		                break;
		            case "sctp":
		                connector = new SctpServerConnector(bindToAddress, 100);
		                break;
		            case "dtls":
		                connector = new DtlsServerConnector(bindToAddress, "certificados/keyStore.jks", "certificados/trustStore.jks", 2000);
		                break;
		            case "wss":
		                connector = new WssServerConnector(bindToAddress, "certificados/keyStore.jks", 2000);
		                break;
		            case "tls":
		                connector = new TlsServerConnector(new SSLUtil("endPass", "keyStore.jks").getSSLContext(), bindToAddress, 2, 2000);
		                break;
		            default:
		                connector = new UDPConnector(bindToAddress);
		                break;
		        }

		        addEndpoint(new CoapEndpoint(connector, config));
		        System.out.println("PubSubBroker, Added endpoint at addr:: " + addr.getHostAddress());
		    }
	
        	}
	}
    }
    
    public void setProtocoloInteraccionCoap(ProtocoloInteraccionCoap _protocoloInteraccionCoap){
	setProtocoloInteraccionCoapSuper(_protocoloInteraccionCoap);
    }
	

    
    
}
