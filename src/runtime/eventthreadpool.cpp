#include <iostream>
#include <stdio.h>
#include <math.h>
#include <algorithm>
#include "event.h"
#include "eventthreadpool.h"

const int numberOfThreads = 4;
const int typeOfEvents    = 20;
const int numberOfEvents  = 100;

extern "C" void
  event_processor(flux::event *ev, flux::queue<flux::event *> *equeue) 
{
  fprintf(stderr, "processing event type: %d\n", ev->type);
  ev->type = ev->type + 1000;

  if (ev->type < 10000)
    equeue->fifo_push(ev);
  else
    delete ev;
}

extern "C" void * eventHandler (void * arg) 
{
  flux::queue<flux::event *> *eventQueue = (flux::queue<flux::event *> *)arg;
  flux::event *e;
  fprintf(stderr, "Starting a thread .... \n");

  while (true) {
    e = eventQueue->lifo_pop();
    if (e == NULL) {
      fprintf(stderr, "Exiting one thread\n");
      break;
    }
    event_processor(e, eventQueue);
  }
}

int main(int argc, char *argv[]) 
{
  flux::eventthreadpool < 4 > eventTP(eventHandler); 
//      =  new flux::eventthreadpool(eventHandler);
  flux::event *ev;

  for (int i = 0; i < numberOfEvents; i++) {
    ev = new flux::event();
    ev->type = i;
    eventTP.add_event(ev);
  }

  //eventTP.killAll();
  eventTP.wait();

  return 0;
}
