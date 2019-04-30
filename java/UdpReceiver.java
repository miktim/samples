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
 *   // define socket interface, if needed
 *   receiver.setInterface(InetAddress.getByName("192.168.1.1")); 
 *   // check or set socket properties, if needed
 *   if (receiver.isMulticastSocket()) {
 *  	((MulticastSocket) receiver.getSocket()).set...();
 *   }
 *   // increase datagram buffer length (256 bytes default), if needed
 *   receiver.setBufferLength(508); // IPv4 guaranteed receive packet size by any host
 *   // bind (join | connect) socket, start listening
 *   receiver.start();
 *    ....
 *   // stop listening, close socket. Restart not possible.
 *   receiver.close();
 *
 * Author:  miktim@mail.ru
 * Created: 2019-03-21
 * Updated: 2019-04-30
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
    private int port;
    private InetAddress rcvAddress; // listening address
    private InetAddress infAddress; // interface address
    private int bufferLength = 256; // 508 - IPv4 guaranteed receive packet size by any host
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
            	} catch (java.net.SocketTimeoutException e) { 
// ignore
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
        if (port < 0) throw new IllegalArgumentException();
        this.port = port;
        rcvAddress = inetAddress != null ? inetAddress : InetAddress.getByName("0.0.0.0");
        if (rcvAddress.isMulticastAddress()) {
// See note:
//   https://docs.oracle.com/javase/7/docs/api/java/net/DatagramSocket.html#setReuseAddress(boolean)
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
        } else {
    	    socket = new DatagramSocket(null);
        }
        this.listener = udpListener;
    }
    
    public void setInterface(InetAddress infInetAddress) throws IOException {
        if (receiver.isAlive()) throw new SocketException("Socket in use");
        if (NetworkInterface.getByInetAddress(infInetAddress) == null) {
            throw new SocketException("No such interface");
        }
        infAddress = infInetAddress;
    }
    
    public InetAddress getInfAddress() {
        return infAddress;
    }
    
    public boolean isMulticastSocket() {
        return rcvAddress.isMulticastAddress();
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
    
    public void start() throws IOException {
        if(socket.isClosed()) throw new java.net.SocketException("Socket is closed");
        if (receiver.isAlive()) return;
        if (!socket.isBound()) bind();
        socket.setSoTimeout(0); // infinite timeout
        receiver.start();
        showSocketInfo();
    }

    private boolean isExternalAddress(InetAddress ia) throws SocketException {
        return !(ia.isMulticastAddress() || ia.isAnyLocalAddress() || ia.isLoopbackAddress()
            || NetworkInterface.getByInetAddress(ia) != null);
    }

    private void bind() throws IOException {
        if (isMulticastSocket()) {
            socket.bind(new InetSocketAddress(port));
            ((MulticastSocket) socket).joinGroup(rcvAddress);
            if (infAddress != null) ((MulticastSocket) socket).setInterface(infAddress);
        } else {
            if (infAddress != null) { 
                socket.bind(new InetSocketAddress(infAddress, port));
                if (rcvAddress.isAnyLocalAddress()) socket.setBroadcast(true);
                else socket.connect(rcvAddress, port);
            } else {
                if (isExternalAddress(rcvAddress)) {
                    socket.bind(new InetSocketAddress(port));
                    socket.connect(rcvAddress, port);
                } else {
                    socket.bind(new InetSocketAddress(rcvAddress, port));
                }
            }
        }
    }
    
    private void showSocketInfo() {
	try {
	    System.out.println("UDP receiver socket is bound to: "
		+ socket.getLocalSocketAddress()
		+ (socket.getBroadcast() ? " broadcast" : "")
		+ (!isMulticastSocket() ? "" :  
		    (" MCgroup: " + rcvAddress 
		    + (rcvAddress.isMCLinkLocal() ? " local" : "")
		    + (infAddress != null ? " interface: " + infAddress : "")))
	    );
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void close() {
        if (!socket.isClosed()) {
    	    try { // ???
        	if (isMulticastSocket()) 
        	    ((MulticastSocket)socket).leaveGroup(rcvAddress);
        	if (socket.isConnected()) socket.disconnect();
    	    } catch (Exception e) {
    		e.printStackTrace();
    	    }
    	    socket.close();
    	}
    }

}
