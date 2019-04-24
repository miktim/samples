//package org.samples.java.udp;

/**
 * UdpReceiver class. Queues up received packets.
 *
 * Usage:
 *   // create UDP receiver listener
 *   UdpReceiver.Listener listener = new UdpReceiver.Listener() {
 *      public void onUdpPacket(DatagramPacket dp) {
 *   		...do something with packet
 *   	}
 *      public void onUdpError(Exception e) {
 *   		e.printStackTrace();
 *      }
 *   };
 *   // open multicast or datagram socket (depends on address)
 *   UdpReceiver receiver =
 *  	new UdpReceiver(10000, InetAddress.getByName("224.0.0.1"), listener);
 *   // bind socket to interface, if needed (datagram socket will be recreated)
 *   receiver.setInterface(InetAddress.getByName("192.168.1.1")); 
 *   // check or set socket properties, if needed
 *   if (receiver.isMulticastSocket()) {
 *  	((MulticastSocket) receiver.getSocket()).set...();
 *   }
 *   // increase datagram buffer length (256 bytes default), if needed
 *   receiver.setBufferLength(508); // IPv4 guaranteed receive packet size by any host
 *   // start listening
 *   receiver.start();
 *    ....
 *   // stop listening, close socket. Restart not possible.
 *   receiver.close();
 *
 * Author:  miktim@mail.ru
 * Created: 2019-03-21
 * Updated: 2019-04-24
 *
 * License: MIT
 */
import java.io.IOException;
import java.net.SocketException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;

public class UdpReceiver {

    public interface Listener {
        public abstract void onUdpPacket(DatagramPacket packet);
        public abstract void onUdpError(Exception e);
    }
    
    private DatagramSocket socket; 
    private InetAddress groupAddress; // multicast group address
    private InetAddress infAddress;   // interface address
    private int bufferLength = 256;   // 508 - IPv4 guaranteed receive packet size by any host
    private Listener listener;
    private Thread receiver = new ReceiverThread();
    
    private final ArrayDeque <DatagramPacket> packetQueue = new ArrayDeque <DatagramPacket> ();

    private class ReceiverThread extends Thread {
	private PacketDequer dequer = new PacketDequer(); 
	
        public void run() {
    	    dequer.start(); 
    	    while (!socket.isClosed()) {
		try {
        	    DatagramPacket dp =
            		new DatagramPacket(new byte[bufferLength], bufferLength);
		    socket.receive(dp);
        	    synchronized(packetQueue) {
            		packetQueue.addLast(dp);
            		packetQueue.notify();
            	    }
            	} catch (Exception e) {
            	    if(socket.isClosed()) break;
		    e.printStackTrace();
            	    listener.onUdpError(e);
    		}
    	    }
    	    dequer.interrupt();
    	    packetQueue.clear();
    	}
    }

    private class PacketDequer extends Thread {
// SO_RCVBUF size = 106496 (Linux x64)
        public void run() {
            while (!socket.isClosed()) {
            	synchronized (packetQueue) {
            	    if (packetQueue.isEmpty()) {
                	try { 
                	    packetQueue.wait();
                	} catch (InterruptedException ie) {
                    	    Thread.currentThread().interrupt();
                	}
            	    } else {
                	listener.onUdpPacket(packetQueue.pollFirst());
            	    }
        	}
    	    }
        }
    }
    
    public UdpReceiver(int port, Listener udpListener) throws Exception {
        new UdpReceiver(port, null, udpListener);
    }

    public UdpReceiver(int port, InetAddress inetAddress, Listener udpListener) 
	    throws Exception {
        InetAddress ia = inetAddress != null ? inetAddress : InetAddress.getByName("0.0.0.0");
        if (ia.isMulticastAddress()) {
            groupAddress = ia;
            socket = new MulticastSocket(port);
            ((MulticastSocket)socket).joinGroup(ia);
        } else {
            socket = new DatagramSocket(port, ia);
            if (ia.isAnyLocalAddress()) socket.setBroadcast(true); // android
        }
        this.listener = udpListener;
    }

    public void setInterface(InetAddress infInetAddress) throws IOException {
	if (receiver.isAlive()) throw new SocketException("Socket in use");
	if (NetworkInterface.getByInetAddress(infInetAddress) == null) {
	    throw new SocketException("No such interface");
	}
	if (isMulticastSocket()) { 
	    ((MulticastSocket) socket).setInterface(infInetAddress);
	} else {
	    int port = socket.getLocalPort();
	    socket.close();
	    socket = new DatagramSocket(port, infInetAddress);
	}
        infAddress = infInetAddress;
    }
    
    public InetAddress getInfAddress() {
	return infAddress;
    }
    
    public boolean isMulticastSocket() {
	return groupAddress != null;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void setBufferLength(int length) throws IllegalArgumentException {
	if (length <= 0) throw new IllegalArgumentException();
        bufferLength = length;
    }

    public int getBufferLength() {
        return bufferLength;
    }
    
    public void start() throws SocketException {
	if(socket.isClosed()) throw new java.net.SocketException("Socket is closed");
	if (receiver.isAlive()) return;
	receiver.start();
	showSocketInfo();
    }
    
    private void showSocketInfo() {
	try {
	    System.out.println("UDP receiver socket is bound to: "
		+ socket.getLocalSocketAddress()
		+ (socket.getBroadcast() ? " broadcast" : "")
		+ (groupAddress == null ? "" :  
		    (" MCgroup: " + groupAddress 
		    + (groupAddress.isMCLinkLocal() ? " local" : "")
		    + (infAddress != null ? " interface: " + infAddress : "")))
	    );
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void close() {
        if (!socket.isClosed()) {
    	    try { // ???
        	if (groupAddress != null) 
        	    ((MulticastSocket)socket).leaveGroup(groupAddress);
    	    } catch (Exception e) {
    		e.printStackTrace();
    	    }
    	    socket.close();
    	}
    }

}
