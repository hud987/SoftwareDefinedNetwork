ECE  50863 Computer Network Systems
Project I: Building a simple software defined network (SDN)

Config.txt holds the route info as seen in project description

udpcontroller.java run with command:
java udpcontroller.java

make sure switches are run on different ports
udpswitch.java run with command:
java udpswitch.java <switchID> <controller hostname> <switch port> -f <neighbor ID> -v


Any code commented out is for hostname functionality. Feel free to remove/add print statements and edit code.


Things left to impliment:
- -f and -v commands
- hostname functionality
- disconnecting and reconnecting switches leads to route bugs
- probably more...
