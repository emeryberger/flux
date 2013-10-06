#include "mImpl.h"

#include <sys/types.h>       // For data types
#include <sys/socket.h>      // For socket(), connect(), send(), and recv()
#include <netdb.h>           // For gethostbyname()
#include <arpa/inet.h>       // For inet_addr()
#include <unistd.h>          // For close()
#include <netinet/in.h>      // For sockaddr_in
#include <signal.h>
#include <sys/poll.h>
#include <fcntl.h>
#include <sys/time.h>

int serverSocket;
int clientID = 100;
ClientList clients;

#define BOARD_HEIGHT 240
#define BOARD_WIDTH 320

inline Client *getClient(int id) {
  for(int i=0;i<clients.size();i++)
    if (clients[i]->id == id)
      return clients[i];
  return NULL;
}

int send(char *address, unsigned short port,
	  char *data, int data_size)
{
  sockaddr_in addr;
  char hostname[32];
  
  sprintf(hostname, "%hhu.%hhu.%hhu.%hhu", address[0],address[1],address[2],address[3]);
  //printf("Sending to: %s:%d\n", hostname, port);
  memset(&addr, 0, sizeof(addr));
  addr.sin_family = AF_INET;
  
  hostent *host;  // Resolve name
  if ((host = gethostbyname(hostname)) == NULL) {
    perror("Host lookup");
  }
  addr.sin_addr.s_addr = *((unsigned long *) host->h_addr_list[0]);
  addr.sin_port = htons(port);

  if (sendto(serverSocket, data, data_size, 0,
             (sockaddr *) &addr, sizeof(addr)) != data_size) {
    perror("Sending");
    return 1;
  }
  return 0;
}


void die(int signal) {
  printf("Shutting down\n");
  close(serverSocket);
  exit(0);
}

void init(int argc, char **argv) {
  int broadcastPermission = 1;
  srand48(time(NULL));
  if (argc < 2) {
    fprintf(stderr, "Usage: %s <port>\n", argv[0]);
    exit(-1);
  }
  int localPort = atoi(argv[1]);
  
  if ((serverSocket = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP)) < 0) {
    perror("Socket creation failed");
    exit(-1);
  }
  
  // Bind the socket to its port
  sockaddr_in localAddr;
  memset(&localAddr, 0, sizeof(localAddr));
  localAddr.sin_family = AF_INET;
  localAddr.sin_addr.s_addr = htonl(INADDR_ANY);
  localAddr.sin_port = htons(localPort);

  if (bind(serverSocket, (sockaddr *) &localAddr, sizeof(sockaddr_in)) < 0) {
    perror("Set of local port failed (bind())");
    exit(-1);
  }
  
  if ((setsockopt(serverSocket, SOL_SOCKET, SO_BROADCAST, 
		  (int *) &broadcastPermission, sizeof(broadcastPermission))) 
      < 0) {
    perror("Set broadcast option");
    exit(-1);
  }

  signal(SIGINT, die);

  printf("Server Started\n");
  
}

int ParseDisconnect (ParseDisconnect_in *in, ParseDisconnect_out *out) {
  out->client = in->client;
  return 0;
}

void teleport(Client *c) {
  c->x = (int)(drand48()*BOARD_WIDTH);
  c->y = (int)(drand48()*BOARD_HEIGHT);
}

int DoConnect (DoConnect_in *in) {
  char buff[2];
  Client *c;
  
  /*
    printf("Connect request from %hhu.%hhu.%hhu.%hhu:%u\n", 
    in->host[0],in->host[1],in->host[2],in->host[3], in->port);
    printf("Assigning clientId: %d\n", clientID);
  */
  
  clientID++;

  buff[0]=0;
  buff[1]=(char)clientID;
  
  c = new Client;
  c->id = clientID;
  c->address = in->host;
  c->port = in->port;
  c->it = (clients.size()==0);

  teleport(c);
  
  clients.push_back(c);

  return send(in->host, in->port, buff, 2);
}

int ParseEngine (ParseEngine_in *in, ParseEngine_out *out) {
  out->client = in->client;
  out->direction = in->data[2];

  return 0;
}

int ParseConnect (ParseConnect_in *in, ParseConnect_out *out) {
  out->host = (char *)malloc(4);
  out->host[0] = *(in->data+2);
  out->host[1] = *(in->data+3);
  out->host[2] = *(in->data+4);
  out->host[3] = *(in->data+5);

  out->port = ntohl(*((int *)(in->data+6)));
  out->client = -1;
  return 0;
}

int ParseMessage (ParseMessage_in *in, ParseMessage_out *out) {
  out->type = in->data[0];
  out->client = in->data[1];
  out->data = in->data;

  //printf("received: %d\n", out->type);
  return 0;
}

long last = -1;

int GetClients (GetClients_out *out) {
  for (int i=0;i<clients.size();i++)
    out->clients.push_back(clients[i]);
   
  return 0;
}

int Listen (Listen_out *out) {
  char buffer[512];
  int bufferLen = 512;
  char *addr;
  short port;
  sockaddr_in clntAddr;
  socklen_t addrLen = sizeof(clntAddr);
  int rtn;
  
  struct pollfd plf;
  plf.fd = serverSocket;
  plf.events = POLLIN;
  
  if (poll(&plf, 1, 1000)) {
    if ((rtn = recvfrom(serverSocket, buffer, bufferLen, 0, 
			(sockaddr *) &clntAddr, (socklen_t *) &addrLen)) < 0) {
      perror("Listen failed");
      return 1;
    }

    addr = inet_ntoa(clntAddr.sin_addr);
    port = ntohs(clntAddr.sin_port);
    
    //printf("Received\n");
    
    out->data = (char *)malloc(rtn);
    memcpy(out->data, buffer, rtn);
    return 0;
  }
  return 1;
}

int ReadMessage (ReadMessage_in *in) {

}

int SendData (SendData_in *in) {
  char buff[3+7*in->clients.size()];
  for (int j=0;j<in->clients.size();j++) {
    Client *recv = in->clients[j];
    buff[0] = 5;
    buff[1] = recv->id;
    buff[2] = in->clients.size();
    int ix = 3;
    for (int i=0;i<in->clients.size();i++) {
      Client *c = in->clients[i];
      buff[ix++] = c->id;
      *((uint16_t *)(buff+ix)) = htons(c->x);
      ix+=2;
      *((uint16_t *)(buff+ix)) = htons(c->y);
      ix+=2;
      buff[ix++] = c->it;
      buff[ix++] = 0;
      
      //printf("%hu (%hu, %hu)\n", *((uint16_t *)(buff+i*7+3)), 
      //*((uint16_t *)(buff+i*7+5)), buff[i*7+7]);
    }
    send(recv->address, recv->port, buff, in->clients.size()*7+3);
  }
}

int ParseTurn (ParseTurn_in *in, ParseTurn_out *out) {
  out->client = in->client;
  out->direction = in->data[2];
  return 0;
}

int ClientMessage (ClientMessage_in *in) {

} 

int DoDisconnect (DoDisconnect_in *in) {
  printf("Disconnect from %d\n", in->client);

  ClientList::iterator it = clients.begin();
  while (it != clients.end() && (*it)->id != in->client)
    it++;
  if (it != clients.end()) {
    clients.erase(it);
  }
}

int DoTurn (DoTurn_in *in) {
  Client *c = getClient(in->client);
  c->x+=in->direction;
  if (c->x < 0) 
    c->x = 0;
  else if (c->x > BOARD_WIDTH)
    c->x = BOARD_WIDTH;
  
  return 0;
}

int DoEngine (DoEngine_in *in) {
  Client *c = getClient(in->client);
  c->y+=in->direction;
  if (c->y < 0)
    c->y = 0;
  else if (c->y > BOARD_HEIGHT)
    c->y = BOARD_HEIGHT;
  return 0;
}

bool isEngineMessage(int type) {
  return type == 2;
}
bool isShootMessage(int type) {
  return type == 4;
}
bool isTurnMessage(int type) {
  return type == 3;
}
bool isConnectMessage(int type) {
  return type == 0;
}
bool isDisconnectMessage(int type) {
  return type == 1;
}

#define THRESH 25

inline bool closeEnough(Client *one, Client *two) {
  return 
    ((one->x-two->x)*(one->x-two->x)+(one->y-two->y)*(one->y-two->y) < THRESH);
}

int Wait(Wait_in *in, Wait_out *out) {
  out->clients = in->clients;
  usleep(100000);
  return 0;
}

int UpdateBoard (UpdateBoard_in *in, UpdateBoard_out *out) {
  out->clients = in->clients;
  if (clients.size() > 0) {
    Client *it = NULL;
    for (int i=0;i<clients.size();i++) {
      if (clients[i]->it) {
	it = clients[i];
	break;
      }
    }
    assert(it);
    for (int i=0;i<clients.size();i++) {
      if (clients[i] != it && closeEnough(clients[i], it)) {
	clients[i]->it = true;
	it->it = false;
	teleport(clients[i]);
	return 0;
      }
    }
  }
  return 0;
}
