# p2p-ufabc
![System Overview](system_overview.png)

P2P system for transferring video files between Peers, intermediated by a centralized server, using UDP for communication with the Server and TCP between Peers.

Project developed for the subject Distributed Systems at Federal University of ABC - Brazil. :brazil:

## External libs
https://github.com/google/gson version 2.8.7

In order to compile the project, copy and paste the external lib to:

` C:\<project_path>\bin `

## Executing the project
From Windows, open the command line (cmd) and change directory to the project path:

` cd C:\<caminho_do_projeto>\bin `

### Server
First, initialize the server. Execute:

` java -cp gson-2.8.7.jar; project.Servidor `

### Peer
Then, the peer:

` java -cp gson-2.8.7.jar; project.Peer `

## Observations
- The Peer path cannot have spaces.
- The file names cannot have spaces or commas.
