# ECE  50863 Computer Network Systems
## Project I: Building a simple software defined network (SDN)

Config.txt holds the route info as seen in project description

### udpcontroller.java run with command:
#### java udpcontroller.java

### udpswitch.java run with command 
(make sure switches are run on different ports):
#### java udpswitch.java \<switchID> \<controller hostname> \<switch port> -f \<neighbor ID> -v
  
Controller updates routing map on each switch change
