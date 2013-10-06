// -*- C++ -*-

#ifndef _MESSAGE_H_
#define _MESSAGE_H_

namespace flux
{

	template <typename STACKTYPE, typename DATATYPE>
  class message
  {
  public:
    message (STACKTYPE * s, DATATYPE d)
    	: stack (s), data (d)
    {
    }
    STACKTYPE * stack;
    DATATYPE data;
  };

};

#endif // _MESSAGE_H_
