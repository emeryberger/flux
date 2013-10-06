#include <iostream>
#include "random.h"

using namespace std;

int
main ()
{
  flux::random rng;
  for (int i = 0; i < 1000; i++)
    {
      cout << rng.next () << endl;
    }
  return 0;
}
