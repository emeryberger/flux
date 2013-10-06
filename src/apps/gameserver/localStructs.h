#include <vector>
#include <functional>

class Client {
 public:
  int id;

  char *address;
  int port;
  
  int x;
  int y;
  int dx;
  int dy;

  bool it;
};

typedef std::vector<Client *> ClientList;
