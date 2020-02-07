package udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class udpswitch1 {
  private static Integer SWITCH_PORT = 5100;
  private static Integer CONTROLLER_PORT = 10000;
  private static Integer K = 5;
  private static Integer M = 3;
  private static String SWITCH_ID;
  private static String CONTROLLER_HOSTNAME;
  private static Integer failedSwitchId;
  private static InetAddress ip;
  private static DatagramSocket client;
  private static Integer currentTime = 0;
  private static boolean verbose;

  // ( Switch ID:[ SwitchHostname,SwitchPort,isAlive ] , ... )
  private static ConcurrentHashMap<String, ArrayList<String>> connectedSwitches = new ConcurrentHashMap<String, ArrayList<String>>();
  // ( Switch ID: lastConnectedTime , ... )
  private static ConcurrentHashMap<String, Integer> connectedTimes = new ConcurrentHashMap<String, Integer>();

  //Thread to always be sending packets
  public static class sendPackets implements Runnable {
    String topology;

    public void run() {

      while (true) {
        try {
          Thread.sleep(K * 1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        topology = "TOPOLOGY_UPDATE " + SWITCH_ID + " ";
        //Loop through all connected switches and send KEEP_ALIVE
        connectedSwitches.entrySet().forEach(entry -> {
          byte[] KeepAliveBytes = new byte[1024];
          String KeepAliveString = "KEEP_ALIVE " + SWITCH_ID;
          KeepAliveBytes = KeepAliveString.getBytes();

          //Only send if switch is marked as alive or no link failure
          if (entry.getValue().get(2) == "1" && (failedSwitchId != Integer.parseInt(entry.getValue().get(1)))) {
            topology += entry.getKey() + " 1 ";
            DatagramPacket KEEP_ALIVE = new DatagramPacket(KeepAliveBytes, KeepAliveBytes.length, ip, Integer.parseInt(entry.getValue().get(1)));
            //System.out.println("[SWITCH SEND] Sending KEEP_ALIVE to switch " + entry.getKey());
            try {
              client.send(KEEP_ALIVE);
              if (verbose)
            	  System.out.println("[SWITCH SEND] KEEP_ALIVE sent to " + entry.getValue().get(1));
            } catch (IOException e) {
              e.printStackTrace();
            }
          } else {
            topology += entry.getKey() + " 0 ";
          }
        });

        try {
          //Send TOPOLOGY_UPDATE to controller
          //System.out.println("[SWITCH SEND] Sending TOPOLOGY_UPDATE: " + topology);
          byte[] topologyUpdateBytes = new byte[1024];
          topologyUpdateBytes = topology.getBytes();
          DatagramPacket TOPOLOGY_UPDATE = new DatagramPacket(topologyUpdateBytes, topologyUpdateBytes.length, ip, CONTROLLER_PORT);
          client.send(TOPOLOGY_UPDATE);
          
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }
    }
  }

  //Thread to always be recieving Packets
  public static class receivePackets implements Runnable {
    public void run() {
      byte[] ReceivedBytes = new byte[1024];
      DatagramPacket RECEIVED = new DatagramPacket(ReceivedBytes, ReceivedBytes.length);

      while (true) {
        //Wait to receive packet
        try {
          //System.out.println("[SWITCH RECV] Waiting for packet...");
          client.receive(RECEIVED);
        } catch (IOException e) {
          e.printStackTrace();
        }
        String[] recievedStringList = new String(RECEIVED.getData(), RECEIVED.getOffset(), RECEIVED.getLength()).split(" ");

        //Check packet label KEEP_ALIVE, ROUTE_UPDATE
        //ROUTE_UPDATE = "ROUTE_UPDATE SwitchId NextHop SwitchId NextHop ..." 
        if (recievedStringList[0].equals("ROUTE_UPDATE")) {
          System.out.println("[SWITCH RECV] Recieved ROUTE_UPDATE: ");
          System.out.println("                SwitchId   Next Hop");
          for (int i=1;i<recievedStringList.length;i+=2){
            System.out.println("                   " + recievedStringList[i] + "     :     " + recievedStringList[i+1]);
          }

        } else if (recievedStringList[0].equals("KEEP_ALIVE")) {
        	//System.out.println("[SWITCH RECV] Recieved KEEP_ALIVE from switch " + recievedStringList[1] + " at " + currentTime);
        	//if the neighbor has link failure, do not process message
        	if (failedSwitchId == Integer.parseInt(recievedStringList[1]))
        		continue;
        	if (verbose)
        		System.out.println("[SWITCH RECV] KEEP_ALIVE from switch" + recievedStringList[1] + " at " + currentTime);
        	
        	connectedTimes.put(recievedStringList[1], currentTime);          
        	//Check if previously dead node is back
        	System.out.println("test1" + recievedStringList[0] + recievedStringList[1]);
        	String isAlive = connectedSwitches.get(recievedStringList[1]).get(2);
        	if (isAlive == "0") {
        		connectedSwitches.get(recievedStringList[1]).set(2, "1");
            //need to repeat initialization procedure, maybe no cause switch, just update host/port
            //have to immediately send topology update to controller
          } 
        }
      }
    }
  }

  //thread to manage clock and check if any switch is dead
  public static class keepTrackOfTime implements Runnable {
    public void run() {
      while (true) {
    	System.out.println("connected time" + connectedTimes);
        //Loop through last connected times
        connectedTimes.entrySet().forEach(entry -> {
          //If value is K*M seconds old and alive, mark as dead
          if (entry.getValue() < (currentTime - ((M * K))) && connectedSwitches.get(entry.getKey()).get(2) == "1") {
            String switchID = entry.getKey();
            System.out.println("[SWITCH CONN] Switch " + switchID + " has disconnected ");
            ArrayList<String> switchInfo = connectedSwitches.get(switchID);
            switchInfo.set(2,"0");
            connectedSwitches.put(entry.getKey(), switchInfo);
            //have to send updated topology to controller
          }
        });      

        //update time and wait 1 second
        currentTime += 1;
        //System.out.println("[SWITCH CONN] Current Time: " + currentTime);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  //-f fails the connection to given switch ID
  //-v adds extra console logs
  //IN ASSIGNEMENT switch <switchID> <controller hostname> <controller port> -f <neighbor ID>
  //RUN THIS VERSION switch <switchID> <controller hostname> <switch port> -f <neighbor ID>
  public static void main(String[] args) throws IOException {
    ArrayList<String> argsList = new ArrayList<String>();  
    ArrayList<String> optsList = new ArrayList<String>();
    verbose = false;
    ArrayList<ArrayList<Integer>> neighborInfo = new ArrayList<ArrayList<Integer>>();

    //handle input args, -f failure, and -v 
    //should probably be put in separate function
    for (int i = 0; i < args.length; i++) {
      switch (args[i].charAt(0)) {
      case '-':
        if (args[i].charAt(1) == 'f') {
          if (args.length-1 == i)
            throw new IllegalArgumentException("Expected arg after: "+args[i]);
          optsList.add(args[i+1]);
          i++;
        } else if (args[i].charAt(1) == 'v') {
          verbose = true;
        } else {
          throw new IllegalArgumentException("Not a valid argument: "+args[i]);
        }
        break;
      default:
        argsList.add(args[i]);
        break;
      }
    }
    if (argsList.size() > 2) {
      SWITCH_ID = argsList.get(0);
      CONTROLLER_HOSTNAME = argsList.get(1);
      SWITCH_PORT = Integer.parseInt(argsList.get(2));
    }
    if (optsList.size() > 0) {
      failedSwitchId = Integer.parseInt(optsList.get(0));
    } else {
    	failedSwitchId = -1;
    }

    ip = InetAddress.getLocalHost();
    client = new DatagramSocket(SWITCH_PORT);

    //format and send inital REGISTER_REQUEST: "REGISTER_REQUEST SWITCH_ID CONTROLLER_HOSTNAME"
    byte[] request = new byte[1024];
    String requestString = "REGISTER_REQUEST " + SWITCH_ID + " " + CONTROLLER_HOSTNAME;
    
    //report failed link in REGISTER_REQUEST
    //if (failedSwitchId != -1)
    //	requestString += " FAILED_LINK " + Integer.toString(failedSwitchId);
    
    request = requestString.getBytes();
    DatagramPacket REGISTER_REQUEST = new DatagramPacket(request, request.length, ip, CONTROLLER_PORT);
    client.send(REGISTER_REQUEST);

    //Wait for REGISTER_RESPONSE
    byte[] response = new byte[1024];
    DatagramPacket REGISTER_RESPONSE = new DatagramPacket(response, response.length);
    System.out.println("[SWITCH] REGISTER_REQUEST sent, waiting for REGISTER_RESPONSE...");  

    String[] responseString;
    client.receive(REGISTER_RESPONSE);
    responseString = new String(REGISTER_RESPONSE.getData(), REGISTER_RESPONSE.getOffset(), REGISTER_RESPONSE.getLength()).split(" "); 

    //when a switch is restarted, it may recieve a KEEP_ALIVE before REGISTER_RESPONSE
    //this ensures that the received packet is the response
    while (!responseString[0].equals("REGISTER_RESPONSE")) {
      client.receive(REGISTER_RESPONSE);
      responseString = new String(REGISTER_RESPONSE.getData(), REGISTER_RESPONSE.getOffset(), REGISTER_RESPONSE.getLength()).split(" "); 
    }
    
    System.out.println("[SWITCH] Recieved REGISTER_RESPONSE ");  
    
    //REGISTER_RESPONSE format: "REGISTER_RESPONSE SwitchID aliveFlag SwitchHostname SwitchPort"
    //write 0 when link failure
    for (int i=1; i < responseString.length; i+=4) {
    	if (Integer.parseInt(responseString[i]) != failedSwitchId)
    		connectedSwitches.put( responseString[i], new ArrayList<String>(Arrays.asList( responseString[i+2], responseString[i+3], "1" )) );
    	else
    		connectedSwitches.put( responseString[i], new ArrayList<String>(Arrays.asList( responseString[i+2], responseString[i+3], "0" )) );
    }
    Thread s = new Thread(new sendPackets());
    Thread r = new Thread(new receivePackets());
    Thread c = new Thread(new keepTrackOfTime());

    s.start();
    r.start();
    c.start();
  }
}
