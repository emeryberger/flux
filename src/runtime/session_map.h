#ifndef __SESSION_MAP__
#define __SESSION_MAP__

#include <map>
#include "loch.h"

namespace flux {
  class session;

  class SessionMap {
  private:
    std::map<int, session *> data;
    flux::loch lk;
    
  public:
    flux::session *getSession(int sessionId) {
      session *s;
      
      lk.lock();
      std::map<int, session *>::iterator it = data.find(sessionId);
      
      if (it == data.end()) {
	s = new flux::session();
	data[sessionId] = s;
      }
      else {
	s = (*it).second;
      }
      lk.unlock();
      return s;
    }

    void destroySession(int sessionId) {
      fprintf(stderr, "Unimplemented!");
    }
  };
};

#endif
