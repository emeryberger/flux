#include <list>
extern "C" {
#include "jpeglib.h"
}
struct image_tag;

struct image_tag {
	char *name;
	int width;
	int height;
	int size;
	std::list<image_tag *>::iterator lru_index;
	//void *image_data;
	JOCTET *image_data;
	int refcount;
};

typedef struct image_tag image_tag;
