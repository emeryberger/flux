#ifndef _GUARD_H_
#define _GUARD_H_

#include "loch.h"

namespace flux
{

  // Acquire & release a lock via
  // the magic of stack allocation :)
  class guard
  {
  public:
    guard (flux::loch & l):_lock (l)
    {
      _lock.lock ();
    }
     ~guard ()
    {
      _lock.unlock ();
    }
  private:
      flux::loch & _lock;
  };

};

#endif // _GUARD_H_
