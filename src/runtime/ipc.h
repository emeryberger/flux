// This looks like C but its really -*- C++ -*-
#ifndef __IPC_H__
#define __IPC_H__

#include "plhash.h"
#include "queue.h"
#include "loch.h"
#include "guard.h"

namespace flux
{

  class messageQueue
  {
  private:
    PLHashTable * queues;
    flux::loch queues_lock;

  public:
    inline messageQueue ()
    {
      queues = PL_NewHashTable
	(20, PL_HashString, PL_CompareStrings, PL_CompareValues, NULL, NULL);
    }

    void create_queue (char *name)
    {
      flux::guard m (queues_lock);
      queue < message * >*q = new queue < message * >;
      PL_HashTableAdd (queues, name, q);
    }

    message *read_message (char *name)
    {
      flux::queue < flux::message * >*q;
      {
	flux::guard m (queues_lock);
	q =
	  (flux::queue <
	   flux::message * >*)PL_HashTableLookupConst (queues, name);
      }
      if (q)
	{
	  message *msg = q->fifo_pop ();
	  return msg;
	}
      return 0;
    }

    void send_message (char *name, void *data, queue < char *>*routing)
    {
      if (name)
	{
	  queue < message * >*q;
	  {
	    flux::guard m (queues_lock);
	    q = (queue < message * >*)PL_HashTableLookupConst (queues, name);
	  }
	  if (q)
	    {
	      message *msg = new message (routing, data);
	      q->fifo_push (msg);
	    }
	}
    }
  };
};
#endif
