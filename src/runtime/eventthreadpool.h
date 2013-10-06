// -*- C++ -*-

#ifndef _EVENTTHREADPOOL_H_
#define _EVENTTHREADPOOL_H_

#include "queue.h"
#include "fred.h"
#include "event.h"
//#include "eventpool.h"

namespace flux
{

  /**
	 * Base class for a threadpool
	 * \author Emery Berger
   **/
	class threadpool_base
	{
		public:
			virtual void worker (void) = 0;

    /**
			 * This gives a thread a number
			 * \author Emery Berger
	 **/
			class poolPair:public std::pair < flux::threadpool_base *, int >
			{
				public:
      // Constructor
					poolPair (flux::threadpool_base * b,
							  int n):std::pair < flux::threadpool_base *, int >(b, n)
					{
					}
			};

	};

  template < int NTHREADS > class eventpool
  {
    public:
      eventpool()
      {
        for (int i = 0; i < NTHREADS; i++) {
          eventQueues[i] = new flux::queue<flux::event *>();
        }
        total = NTHREADS;
        position  = 0;
      }

      virtual ~eventpool(void) {}

      inline void put_event(flux::event *ev, int index) {
        eventQueues[index]->fifo_push(ev);
      }

      inline void add_event(flux::event *ev)
      {
        // NOTE we can use better alg to put event into 
        // different queues
        eventQueues[position]->fifo_push(ev);
        position++;
        if (position > total - 1)
          position = position - total;
      }

      void killAll (void) 
      {
        for (int i=0; i< NTHREADS; i++)
        {
          eventQueues[i]->fifo_push(NULL);
        }
      }

      flux::queue<event *> *getEventQueue(int queueIndex) {
	      //fprintf(stderr, "Provide Index %d : address %lx\n", queueIndex, eventQueues[queueIndex]);
        return eventQueues[queueIndex];
      }

    private:
      // event queues on per thread
      flux::queue<flux::event *> *eventQueues[NTHREADS];

      int position; // curent queue to add event
      int total;    // total queues
  };

  template < int NTHREADS > class eventthreadpool : public threadpool_base
  {
    public:
      eventthreadpool(void *(*eventhandler) (void *), void *args, 
                      flux::eventpool<NTHREADS> *eventPool)
      {
        pool = eventPool;
        for (int i = 0; i < NTHREADS; i++) {
		//fprintf(stderr, "creating thread %d\n", i);
          threads[i].create (eventhandler, args);
        }
      }

      virtual ~eventthreadpool(void) {}

      //inline void add_event(flux::event *ev)
      //{
      //  pool->add_event();
      //}

      void worker (void) 
      {
      }

      void killAll (void) 
      {
        pool->killAll();
      }

      void killOne (void)
      {
      }

      void wait(void) 
      {
        for ( int i = 0; i < NTHREADS; i++)
        {
          threads[i].join();
        }
      }

      //flux::queue<event *> *getEventQueue(int queueIndex) {
      //  return pool->getEventQueue(queueIndex);
      //}

    private:
      // event queues on per thread
      flux::eventpool<NTHREADS> *pool;

      // thread
      flux::fred threads[NTHREADS];
  };

};
#endif // _EVENTTHREADPOOL_H_
