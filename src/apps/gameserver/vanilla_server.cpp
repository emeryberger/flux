#include "localStructs.h"

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

pthread_t data_thread;
pthread_mutex_t client_lock;

#define BOARD_HEIGHT 240
#define BOARD_WIDTH 320

#define CONNECT 0
#define DISCONNECT 1
#define ENGINE 2
#define TURN 3

inline Client *getClient(int id) {
  Client *c = NULL;
  pthread_mutex_lock(&client_lock);
  for(int i=0;i<clients.size();i++)
    if (clients[i]->id == id) {
      c = clients[i];
      break;
    }
  pthread_mutex_unlock(&client_lock);
  return c;
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

void teleport(Client *c) {
  c->x = (int)(drand48()*BOARD_WIDTH);
  c->y = (int)(drand48()*BOARD_HEIGHT);
}

int DoConnect (char *host, int port) {
  char buff[2];
  Client *c;
  
  /**/
  printf("Connect request from %hhu.%hhu.%hhu.%hhu:%u\n", 
	 host[0],host[1],host[2],host[3], port);
  printf("Assigning clientId: %d\n", clientID);
  /**/
  
  clientID++;

  buff[0]=0;
  buff[1]=(char)clientID;
  
  c = new Client;
  c->id = clientID;
  c->address = strndup(host, 4);
  c->port = port;
  c->it = (clients.size()==0);

  teleport(c);
  
  clients.push_back(c);

  return send(host, port, buff, 2);
}

int ParseEngine (char *data, int *direction) {
  *direction = data[2];
  return 0;
}

int ParseConnect (char *data, char *host, int *port, char *client) {
  host[0] = *(data+2);
  host[1] = *(data+3);
  host[2] = *(data+4);
  host[3] = *(data+5);

  *port = ntohl(*((int *)(data+6)));
  printf("Connect request from %hhu.%hhu.%hhu.%hhu:%u\n", 
	 host[0],host[1],host[2],host[3], port);
  *client = -1;
  return 0;
}

int ParseMessage (char *data, char *type, char *client) {
  *type = data[0];
  *client = data[1];

  //printf("received: %d\n", *type);
  return 0;
}

long last = -1;


int GetClients (std::vector<Client *> *client_out) {
  pthread_mutex_lock(&client_lock);
  for (int i=0;i<clients.size();i++)
    client_out->push_back(clients[i]);
  pthread_mutex_unlock(&client_lock);

  return 0;
}


int Listen (char *data) {
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
    
    memcpy(data, buffer, rtn);
    return 0;
  }
  return 1;
}

int SendData (std::vector<Client *> *client_in) {
  char buff[3+5*client_in->size()];
  for (int j=0;j<client_in->size();j++) {
    Client *recv = (*client_in)[j];
    buff[0] = 5;
    buff[1] = recv->id;
    buff[2] = client_in->size();
    int ix = 3;
    for (int i=0;i<client_in->size();i++) {
      Client *c = (*client_in)[i];
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
    send(recv->address, recv->port, buff, client_in->size()*7+3);
  }
}

int ParseTurn (char *data, int *direction) {
  *direction = data[2];
  return 0;
}

int DoDisconnect (int client) {
  printf("Disconnect from %d\n", client);

  ClientList::iterator it = clients.begin();
  while (it != clients.end() && (*it)->id != client)
    it++;
  if (it != clients.end()) {
    clients.erase(it);
  }
}

int DoTurn (int client, int direction) {
  Client *c = getClient(client);
  c->x+=direction;
  if (c->x < 0) 
    c->x = 0;
  else if (c->x > BOARD_WIDTH)
    c->x = BOARD_WIDTH;
  
  return 0;
}

int DoEngine (int client, int direction) {
  Client *c = getClient(client);
  c->y+=direction;
  if (c->y < 0)
    c->y = 0;
  else if (c->y > BOARD_HEIGHT)
    c->y = BOARD_HEIGHT;
  return 0;
}

#define THRESH 25

inline bool closeEnough(Client *one, Client *two) {
  return 
    ((one->x-two->x)*(one->x-two->x)+(one->y-two->y)*(one->y-two->y) < THRESH);
}

int UpdateBoard () {
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

void *DataSender(void *arg) {
  std::vector<Client *> client_out;
  
  while (true) {
    UpdateBoard();
    client_out.clear();
    GetClients(&client_out);
    SendData(&client_out);
    usleep(100000);
  }
}

int main(int argc, char **argv) {
  char data[512];
  char host[4];
  init(argc, argv);
  
  pthread_mutex_init(&client_lock, NULL);
  
  pthread_create(&data_thread, NULL, DataSender, NULL);
  
  while (true) {
    char type;
    char client;
    int direction;
    int port;
    
    if (Listen(data))
      continue;
    ParseMessage(data, &type, &client);

    switch(type) {
    case CONNECT:
      ParseConnect(data, host, &port, &client);
      pthread_mutex_lock(&client_lock);
      DoConnect(host, port);
      pthread_mutex_unlock(&client_lock);
      break;
    case DISCONNECT:
      pthread_mutex_lock(&client_lock);
      DoDisconnect(client);
      pthread_mutex_unlock(&client_lock);
      break;
    case ENGINE:
      ParseEngine(data, &direction);
      DoEngine(client, direction);
      break;
    case TURN:
      ParseTurn(data, &direction);
      DoTurn(client, direction);
      break;
    default:
      fprintf(stderr, "Unknown message:\t%d\n", &type);
      break;
    }
  }
}

