package udp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

public class udpcontroller {

  private static int PORT_CONTROLLER = 10000;
  private static Integer K = 5;
  private static Integer M = 3;  
  private static InetAddress ip;
  private static DatagramSocket controller;
  private static Integer currentTime = 0;
  //( SwitchId:[ SwitchId: BW,SwitchId: BW,... ] , ...)
  private static HashMap<String, HashMap<String, String>> switchNeighborsAndBwMapFinal = new HashMap<String, HashMap<String, String>>();
  private static HashMap<String, HashMap<String, String>> switchNeighborsAndBwMap = new HashMap<String, HashMap<String, String>>();
  //( Switch ID:[ SwitchHostname, SwitchPort, isAlive] , ... )
  private static ConcurrentHashMap<String, ArrayList<String>> switchHostnamePortAlive = new ConcurrentHashMap<String, ArrayList<String>>();
  // ( Switch ID: lastConnectedTime , ... )
  private static ConcurrentHashMap<String, Integer> switchLastConectedTime = new ConcurrentHashMap<String, Integer>();
  //( SwitchId:[ SwitchId:NextHop, SwitchId:NextHop,... ] , ...)
  private static HashMap<String, HashMap<String, String>> switchesAllNextHops = new HashMap<String, HashMap<String, String>>();
  
  //( SwitchID:[ SwitchID:failureTo], ...)
  //private static HashMap<String, String> failedLinkList = new HashMap<String, String>();
  
  //controller isn't always sending packets so this thread isn't necessary
  public static class sendPackets implements Runnable {
    public void run() { 
    }
  }

  //Thread to always recieve packets
  public static class receivePackets implements Runnable {
    public void run() {
      byte[] switchPacketBytes = new byte[1024];
      DatagramPacket SWITCH_PACKET = new DatagramPacket(switchPacketBytes, switchPacketBytes.length);

      while(true){
        try {
          controller.receive(SWITCH_PACKET);
        } catch (IOException e) {
          e.printStackTrace();
        }
        String[] switchPacketList = new String(SWITCH_PACKET.getData(), SWITCH_PACKET.getOffset(), SWITCH_PACKET.getLength()).split(" ");

        if (switchPacketList[0].equals("REGISTER_REQUEST")) {
        	
          System.out.println("is 2 alive???" + switchHostnamePortAlive.get("2").get(0) + switchHostnamePortAlive.get("2").get(1) + switchHostnamePortAlive.get("2").get(2));
        	
          String aliveSwitchId = switchPacketList[1];
          String aliveSwitchHostname = switchPacketList[2];
          String aliveSwitchPort = Integer.toString(SWITCH_PACKET.getPort());
          switchHostnamePortAlive.put(aliveSwitchId, new ArrayList<String>(Arrays.asList( aliveSwitchHostname, aliveSwitchPort, "1" )) );
          switchLastConectedTime.put(aliveSwitchId, currentTime);
          System.out.println("[CONTROLLER RECV] recieved REGISTER_REQUEST from switch " + switchPacketList[1]);  

          switchNeighborsAndBwMap.forEach((id,hshmp) -> {
            String aliveSwitchNeighborBw = switchNeighborsAndBwMapFinal.get(id).get(aliveSwitchId);
            if (aliveSwitchNeighborBw != null) {
              switchNeighborsAndBwMap.get(id).put(aliveSwitchId, aliveSwitchNeighborBw);
            }
          });

          HashMap<String, String> aliveSwitchNewNeighbors = new HashMap<String, String>();
          switchNeighborsAndBwMapFinal.get(aliveSwitchId).forEach((neighborSwitchId, bw) -> {
            if (switchHostnamePortAlive.get(neighborSwitchId).get(2).equals("1")){
              String newNeighborSwitchId = neighborSwitchId;
              aliveSwitchNewNeighbors.put(newNeighborSwitchId, bw);
            }
          });
          switchNeighborsAndBwMap.put(aliveSwitchId, aliveSwitchNewNeighbors);

          System.out.println("is 2 alive???" + switchHostnamePortAlive.get("2").get(0) + switchHostnamePortAlive.get("2").get(1) + switchHostnamePortAlive.get("2").get(2));
          
          sendREGISTER_RESPONSE(aliveSwitchId, aliveSwitchHostname, Integer.parseInt(aliveSwitchPort));
          
          calculateRoutes();
          sendROUTE_UPDATE();
          
        } else if (switchPacketList[0].equals("TOPOLOGY_UPDATE")) {
          String switchId = switchPacketList[1];
          switchLastConectedTime.put(switchPacketList[1], currentTime);
          //System.out.println("[CONTROLLER RECV] recieved TOPOLOGY_UPDATE from switch " + switchPacketList[1]);  

          for (int i=2; i<switchPacketList.length; i+=2){
            //System.out.println("Switch isAlive: " + switchPacketList[i+1]);  
            //System.out.println(switchHostnamePortAlive.get(switchPacketList[i]).get(2));  

            //if (switchPacketList[i+1].equals("0") && switchHostnamePortAlive.get(switchPacketList[i]).get(2).equals("1")) {
/*
            if (switchPacketList[i+1].equals("0") && switchHostnamePortAlive.get(switchPacketList[i]).get(2).equals("0")) {
              String deadSwitchId = switchPacketList[i];
              System.out.println(switchId + " " + switchPacketList[i] + " " + switchPacketList[i+1]);
              System.out.println("[CONTROLLER RECV] dead switch: " + deadSwitchId);
              
              ArrayList<String> switchInfo = switchHostnamePortAlive.get(deadSwitchId);
              switchInfo.set(2, "0");
              switchHostnamePortAlive.put(deadSwitchId, switchInfo);

              switchNeighborsAndBwMap.remove(deadSwitchId);
              switchNeighborsAndBwMap.forEach((id,hshmp) -> {
                switchNeighborsAndBwMap.get(id).remove(deadSwitchId);
              });
            }
*/

//              calculateRoutes();
//              sendROUTE_UPDATE();


            //if (switchPacketList[i+1].equals("0") && switchHostnamePortAlive.get(switchPacketList[i]).get(2).equals("1")) {
        	  if (switchHostnamePortAlive.get(switchPacketList[i]).get(2).equals("0"))
        		  continue;
        	  else if (switchPacketList[i+1].equals("0")) {
            	//A link is failed
            	String failedSwitchId = switchPacketList[i];
            	System.out.println(switchId + " " + switchPacketList[i] + " " + switchPacketList[i+1]);
            	
            	if(switchNeighborsAndBwMap.get(switchId).get(switchPacketList[i]) != null) {            		
            		switchNeighborsAndBwMap.get(switchId).remove(failedSwitchId);      
            		
                	System.out.println("CONTROLLER RECV failed link from " + switchId + " " + failedSwitchId);
            		calculateRoutes();
            		sendROUTE_UPDATE();
            	} else
            		continue;
              } else {
                  if (switchNeighborsAndBwMap.get(switchId).get(switchPacketList[i]) != null)
                	  continue;
                  else {
                	  String bw = switchNeighborsAndBwMapFinal.get(switchId).get(switchPacketList[i]);
                      switchNeighborsAndBwMap.get(switchId).put(switchPacketList[i], bw);
                      
                      System.out.println("CONTROLLER RECV good link from " + switchId + " " + switchPacketList[i]);
                      calculateRoutes();
                      sendROUTE_UPDATE();
                      }
              }

          }            
        }

      }
    }
  } 
  
  //thread to keep track of time and dead switches
  public static class keepTrackOfTime implements Runnable {
    public void run() {
      while (true) {
        //Loop through last connected times
        switchLastConectedTime.entrySet().forEach(entry -> {
          //If value is K*M seconds old and alive, mark as dead
          if (entry.getValue() < (currentTime - (M * K)) && switchHostnamePortAlive.get(entry.getKey()).get(2) == "1") {
            String deadSwitchId = entry.getKey();
            ArrayList<String> switchInfo = switchHostnamePortAlive.get(entry.getKey());
            switchInfo.set(2,"0");
            switchHostnamePortAlive.put(deadSwitchId, switchInfo);
            System.out.println("[CONTROLLER CONN] Switch " + deadSwitchId + " timed out");
            switchNeighborsAndBwMap.remove(deadSwitchId);
            switchNeighborsAndBwMap.forEach((id,hshmp) -> {
              switchNeighborsAndBwMap.get(id).remove(deadSwitchId);
            });

            calculateRoutes();
            sendROUTE_UPDATE();
          }
        });      

        //update time and wait 1 second
        currentTime += 1;
        //System.out.println("[CONTROLLER CONN] Current Time: " + currentTime);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  //REGISTER_RESPONSE format "REGISTER_RESPONSE SwitchID aliveFlag SwitchHostname SwitchPort SwitchID aliveFlag..."
  public static void sendREGISTER_RESPONSE(String switchId, String switchHostname, Integer switchPort) {
    String registerResponseString = "REGISTER_RESPONSE ";
    HashMap<String, String> switchNeighborPortsInfo = switchNeighborsAndBwMapFinal.get(switchId);

    for ( Map.Entry switchNeighborPort : switchNeighborPortsInfo.entrySet() ) {

      String hostname = switchHostnamePortAlive.get(switchNeighborPort.getKey()).get(0);
      String port = switchHostnamePortAlive.get(switchNeighborPort.getKey()).get(1);
      String isAlive = switchHostnamePortAlive.get(switchNeighborPort.getKey()).get(2);
      registerResponseString += switchNeighborPort.getKey() + " " + isAlive + " " + hostname + " " + port + " ";
    }
    //System.out.println(registerResponseString);

    byte[] response = new byte[1024];
    response = registerResponseString.getBytes();
    DatagramPacket REGISTER_RESPONSE = new DatagramPacket(response, response.length, ip, switchPort);
    
    try {
      System.out.println("[CONTROLLER  REG_RESP] Sending REGISTER_RESPONSE to switch " + switchId); 
      controller.send(REGISTER_RESPONSE);
      System.out.println(registerResponseString);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  //Send relevent routing map to switches
  //switchesAllNextHops format SwitchId:[ SwitchId:NextHop, SwitchId:NextHop,... ] , ...)
  //ROUTE_UPDATE format "ROUTE_UPDATE SwitchId NextHop SwitchId NextHop ..." 
  //ConnectedSwitches format ( Switch ID:[ SwitchHostname, SwitchPort, isAlive] , ... )
  public static void sendROUTE_UPDATE(){
    switchHostnamePortAlive.entrySet().forEach( entry -> {
      if (entry.getValue().get(2).equals("1")) {

        String switchId = entry.getKey();
        //Hostname stuff that needs to be implimented
        //String switchHostname = entry.getValue().get(0);
        Integer switchPort = Integer.parseInt(entry.getValue().get(1));
        String routeUpdateString = "ROUTE_UPDATE ";
        
        //get hashmap for current switch [ SwitchId:NextHop, SwitchId:NextHop,... ]
        Iterator neighbor = switchesAllNextHops.get(entry.getKey()).entrySet().iterator();
        while (neighbor.hasNext()) {
          Map.Entry pair = (Map.Entry)neighbor.next();
          routeUpdateString += pair.getKey() + " " + pair.getValue() + " ";
        }

        byte[] routeUpdateBytes = new byte[1024];
        routeUpdateBytes = routeUpdateString.getBytes();
        DatagramPacket ROUTE_UPDATE = new DatagramPacket(routeUpdateBytes, routeUpdateBytes.length, ip, switchPort);

        try {
          //System.out.println("[CONTROLLER] ROUTE_UPDATE for switch " + switchId + ": " + routeUpdateString); 
          //System.out.println("[CONTROLLER] Sending to switch " + switchId); 
          controller.send(ROUTE_UPDATE);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
    return;
  }

  public static void calculateRoutes() {
    //visit all combinations of switch pairs: 1-2, 1-3, 1-4, 1-5, 1-6, 2-3, 2-4...
    HashSet<String> visited = new  HashSet<String>();
    switchNeighborsAndBwMap.entrySet().forEach(entry->{
      HashMap<String, String> placeholder = new  HashMap<String, String>();
      switchesAllNextHops.put(entry.getKey(), placeholder);
    });
    switchNeighborsAndBwMap.entrySet().forEach(entry->{
      visited.add(entry.getKey());
      switchNeighborsAndBwMap.entrySet().forEach(innerEntry->{
        if (!visited.contains(innerEntry.getKey()))
        widestPath(entry.getKey(),innerEntry.getKey());
      });
    });
    return;
  }

  public static void widestPath(String start, String end) {
    //System.out.println("Running widest Path on switch pair " + start + " - " + end);
    
    HashSet<String> visited = new  HashSet<String>();
    HashMap<String, Integer> bw = new HashMap<String, Integer>();
    HashMap<String, String> previouslyVisited = new HashMap<String, String>();
    PriorityQueue<String> toVisit = new PriorityQueue<String>(switchNeighborsAndBwMap.size(),(a,b) -> bw.get(b) - bw.get(a));
    //fill distance hashmap with infinte value
    switchNeighborsAndBwMap.entrySet().forEach( entry -> {
      bw.put(entry.getKey(), Integer.MAX_VALUE);
    });
    toVisit.add(start);

    //check all switches
    while (toVisit.size() > 0) {
      String currentSwitch = toVisit.poll();
      visited.add(currentSwitch);

      //neighborSwitches format[ SwitchId:BW , SwitchId:BW , ... ]
      HashMap<String, String> neighborSwitches = switchNeighborsAndBwMap.get(currentSwitch);
      Integer currentBw = bw.get(currentSwitch);
      //check all neighbors for current switch
      for (Map.Entry neighborSwitch : neighborSwitches.entrySet()){

        if (!visited.contains(neighborSwitch.getKey())) {

          String neighborId = neighborSwitch.getKey().toString();
          Integer neighborBw = Integer.parseInt(neighborSwitch.getValue().toString());
          Integer incomingBw = Integer.min(neighborBw, currentBw);

          if (incomingBw < bw.get(neighborId)){
            bw.put(neighborId, neighborBw);
            previouslyVisited.put(neighborId,currentSwitch);
          } 
          //have to update bw before using the comparator
          toVisit.add(neighborId);

          if ( neighborId.equals(end) ) {
            //switchesAllNextHops = ( SwitchId:[ [SwitchId, NextHop],[SwitchId, NextHop],... ] , ...)
            //System.out.println("Found end switch " + neighborId + " with bandwidth " + bw.get(neighborId));
            //System.out.println("Next hop from end switch " + end + " is " + previouslyVisited.get(neighborId));
            switchesAllNextHops.get(end).put(start,previouslyVisited.get(neighborId));

            while (previouslyVisited.get(neighborId) != start) {
              neighborId = previouslyVisited.get(neighborId);
            }
            //System.out.println("Next hop from start switch " + start + " is " + neighborId);
            switchesAllNextHops.get(start).put(end,neighborId);
            return;
          }
        }
      }
    }
  }

  public static void main(String[] args) throws SocketException, IOException {
    controller = new DatagramSocket(PORT_CONTROLLER);
    ip = InetAddress.getLocalHost();
    //Map switch connections given in config.txt
    //switchMap format ( SwitchId:[ [SwitchId, BW],[SwitchId, BW],... ] , ...)
    File file = new File("./config.txt"); 
    BufferedReader config = new BufferedReader(new FileReader(file)); 

    int switchCount = Integer.parseInt(config.readLine());
    System.out.println("[CONTROLLER] Switches in network: " + switchCount); 

    //Parse config.txt, mapping all switch connections to switchMap
    String line; 
    while ((line = config.readLine()) != null) {
      String[] switchConnection = line.split(" ");
      String switchOne = switchConnection[0];
      String switchTwo = switchConnection[1];
      String bandwith = switchConnection[2];

      if (switchNeighborsAndBwMapFinal.containsKey(switchOne)){
        switchNeighborsAndBwMapFinal.get(switchOne).put(switchTwo , bandwith);
        switchNeighborsAndBwMap.get(switchOne).put(switchTwo , bandwith);
      } else {
        HashMap<String, String> connectionFromOneFinal = new HashMap<String, String>(); 
        connectionFromOneFinal.put(switchTwo , bandwith);
        switchNeighborsAndBwMapFinal.put(switchOne,connectionFromOneFinal);
        
        HashMap<String, String> connectionFromOne = new HashMap<String, String>(); 
        connectionFromOne.put(switchTwo , bandwith);
        switchNeighborsAndBwMap.put(switchOne,connectionFromOne);
      }
      if (switchNeighborsAndBwMapFinal.containsKey(switchTwo)){
        switchNeighborsAndBwMapFinal.get(switchTwo).put(switchOne , bandwith);
        switchNeighborsAndBwMap.get(switchTwo).put(switchOne , bandwith);
      } else {
        HashMap<String, String> connectionFromTwoFinal = new HashMap<String, String>();
        connectionFromTwoFinal.put(switchOne , bandwith);
        switchNeighborsAndBwMapFinal.put(switchTwo, connectionFromTwoFinal);
        
        HashMap<String, String> connectionFromTwo = new HashMap<String, String>();
        connectionFromTwo.put(switchOne , bandwith);
        switchNeighborsAndBwMap.put(switchTwo, connectionFromTwo);
      }
    } 
    
    byte[] buf = new byte[1024];
    DatagramPacket REGISTER_REQUEST = new DatagramPacket(buf, buf.length);

    //Wait for all switches to connect
    while(switchHostnamePortAlive.size() < switchCount) {

      System.out.println("[CONTROLLER] Switches connected: " + switchHostnamePortAlive.size()); 
      System.out.println("[CONTROLLER] Waiting for switch packet: "); 

      controller.receive(REGISTER_REQUEST);
      System.out.println("[CONTROLLER] Received packet: "); 
      String[] request = new String(REGISTER_REQUEST.getData(), REGISTER_REQUEST.getOffset(), REGISTER_REQUEST.getLength()).split(" ");

      String switchId = request[1];
      String switchHostname = request[2];
      String switchPort = Integer.toString(REGISTER_REQUEST.getPort());
      switchHostnamePortAlive.put(switchId, new ArrayList<String>(Arrays.asList( switchHostname, switchPort, "1" )) );
      
      //set failed link
      /*
      if (request.length > 2) {
    	  String linkTo = request[4];
    	  failedLinkList.put(switchId, linkTo);
      }
      */
      //System.out.println("Switch ID: " + switchId);
      //System.out.println("Switch Hostname: " + switchHostname);
      //System.out.println("Switch Port: " + switchPort);
    }

    System.out.println("[CONTROLLER] Connected to all switches"); 

    // Update the map with failedLinkList
    
    
    //REGISTER_RESPONSE format "REGISTER_RESPONSE SwitchID aliveFlag SwitchHostname SwitchPort SwitchID aliveFlag ..."
    switchHostnamePortAlive.entrySet().forEach( entry -> {
      String switchId = entry.getKey();
      //String switchHostname = entry.getValue().get(0);
      Integer switchPort = Integer.parseInt(entry.getValue().get(1));
      sendREGISTER_RESPONSE(switchId, "switchHostname", switchPort);
    });
    
    calculateRoutes();
    sendROUTE_UPDATE();

    Thread s = new Thread(new sendPackets());
    Thread r = new Thread(new receivePackets());
    Thread t = new Thread(new keepTrackOfTime());

    s.start();
    r.start();
    t.start();
  }
}