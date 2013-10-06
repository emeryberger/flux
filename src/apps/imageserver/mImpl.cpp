#include <sys/types.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <sys/stat.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <poll.h>
#include <string>
#include "mImpl.h"
#include <sys/types.h>

extern "C" {
#include "memory-dest.c"
}

#include <vector>
#include <ext/hash_set>
#include <list>

#define BUFFER_SIZE     8192
#define MAX_CACHE_SIZE  20
#define SIZEOF sizeof
#define OUTPUT_BUF_SIZE  50*4096	/* choose an efficiently fwrite'able size */


using namespace __gnu_cxx; // HACK This is very non-portable

// Check if two image_tags refer to the same compressed image
struct eq_image_tag
{
	bool operator()(const image_tag* t1, const image_tag* t2) const
	{
		return strcmp(t1->name, t2->name) == 0 &&
				t1->width == t2->width &&
				t1->height == t2->height;
	}
};

struct image_tag_hash
{
	size_t operator()(const image_tag* tag) const
	{
		size_t hash = 65734; // Random number to start with...
		
		char *p = tag->name;
		while (*p != 0)
			hash += *(p++);
		
		return hash + tag->width * tag->height;
	}
};

struct image_t
{
	int m_height;
	int m_width;
	unsigned char * m_data;
};

std::list<image_tag *> lru_q = std::list<image_tag *>();
hash_set<image_tag *, image_tag_hash, eq_image_tag> cache = hash_set<image_tag *, image_tag_hash, eq_image_tag>();

int s;
struct sockaddr_in server_addr;
char *root;
int root_len;

fd_set read_fds;
int fd_max;
struct timeval select_timeout;

int socket_in_use[1024];

int suffixTest(char *val, char *suffix) {
	int len = strlen(val);
	int s_len = strlen(suffix);
	int i;
	
	for (i=0;i<s_len;i++) {
		if (val[len-i]!=suffix[s_len-i])
			return 0;
	}
	
	return 1;
}

int parse_request(char *request, char *name, int *width, int *height)
{
	std::string str (request);
	if (str.at(0) == '/')
		str.erase(0,1);
	
	unsigned int loc = str.find(".jpg", 0);
	if( loc == std::string::npos )
	{
		return 0;
	}
	
	char buf[256];
	strncpy(buf, str.c_str(), loc);
	buf[loc] = 0;
	
	sprintf(name, "%s.ppm", buf);
	
	unsigned int loc1 = str.find( "=", 0 );
	if( loc1 == std::string::npos )
	{	
		return 0;
	}
	std::string str1 = str.substr(loc1+1);
	
	*width = atoi(str1.c_str());
	
	unsigned int loc2 = str.find( "=", loc1+1);
	if( loc2 == std::string::npos )
	{	
		return 0;
	}
	std::string str2 = str.substr(loc2+1);
	
	*height = atoi(str2.c_str());
	
	return 1;
}

void free_image_tag(image_tag *tag) {
	if (tag != NULL) {
		if (tag->name != NULL)
			delete tag->name;
		if (tag->image_data != NULL)
			delete tag->image_data;
		delete tag;
	}
}

// Increment the reference count and move to the tail of the LRU
void reference_image_tag(image_tag *tag) {
	tag->refcount++;
	lru_q.erase(tag->lru_index);
	lru_q.push_back(tag);
	tag->lru_index = lru_q.end();
	tag->lru_index--;
}

inline char *makeHeaders(char *content, const char *close_hdr, int length) {
	
}

//printf("length: %d\n", (int)str.length() );

void returnSocket(int socket) {
	socket_in_use[socket] = 0;
}

void closeSocket(int socket) {
	close(socket);
	if (socket > -1) {
		if (socket < 1024)
			socket_in_use[socket] = 2;
		else 
			printf("ERR, socket to large\n");
	}
}


image_t * read_ppm( const char * file_name )
{
	FILE * fl_pf = fopen( file_name, "rb" );
	image_t * fo = new image_t;

			// read stupid ascii data
	char fl_buf[256] = { 0 };
	fgets( fl_buf, 256, fl_pf );
	if( fl_buf[0] != 'P' || fl_buf[1] != '6' )
	{
		printf("Bad ID in file %s\n: ", file_name);
		return NULL;
	}

	// read in dimensions
	fgets( fl_buf, 256, fl_pf );
	sscanf( fl_buf, "%d %d", &fo->m_width, &fo->m_height );
	//cout << "width " << fo->m_width << " height " << fo->m_height << endl;
	fgets( fl_buf, 256, fl_pf ); // ignore this stupid line

	// alloc image data
	fo->m_data = new unsigned char[ fo->m_width * fo->m_height * 3 ];

			// read image data
	fread( fo->m_data, sizeof(unsigned char), fo->m_width * fo->m_height * 3, fl_pf );
	fclose( fl_pf );
	return fo;
}
///////////////////////////////////////////////////////////////////////////////////////////////////
// compress to JPG
///////////////////////////////////////////////////////////////////////////////////////////////////

void jpeg_compress(__u8 *buffer, 		// input
				   int width,
				   int height,
				   JOCTET *out_buff, 	// output  LIB JPG's data type
				   int *size) 			// return value of the size of the created JPG
{
	struct jpeg_compress_struct cinfo;
	struct jpeg_error_mgr jerr;
	JSAMPROW row_pointer[1];	/* pointer to JSAMPLE row[s] */
	int row_stride;		/* physical row width in image buffer */

	cinfo.err = jpeg_std_error(&jerr);
	jpeg_create_compress(&cinfo);

	jpeg_mem_dest(&cinfo, out_buff, size);
	cinfo.image_width = width;
	cinfo.image_height = height;
	cinfo.input_components = 3;		
	cinfo.in_color_space = JCS_RGB;

	__u8 *data = buffer;

	jpeg_set_defaults(&cinfo);

	jpeg_start_compress(&cinfo, TRUE);
	row_stride = width * 3;
	while (cinfo.next_scanline < cinfo.image_height) {
		row_pointer[0] = &data[cinfo.next_scanline * row_stride];
		(void) jpeg_write_scanlines(&cinfo, row_pointer, 1);
	}
	jpeg_finish_compress(&cinfo);
  
	jpeg_destroy_compress(&cinfo);

//	printf("Width %d, Height %d, Size: %d\n", width, height, *size);
	/* And we're done! */
}



///////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////
////////////////				FLUX FUNCTIONS BELOW				///////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////
void init(int argc, char **argv) {
	int old_flags;
	
	for (int i=0;i<1024;i++)
		socket_in_use[i] = 2;
	
	s = socket(AF_INET,SOCK_STREAM,0);
	int val = 1;
	if (setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &val, sizeof(val)) < 0)
	{
		perror("setsockopt: ");
		exit(1);
	}
	
	if (argc < 3) {
		fprintf (stderr, "Usage: %s <port-number> <root-dir>\n", argv[0]);
		exit(1);
	}
	
	server_addr.sin_family = AF_INET;
	server_addr.sin_port = htons(atoi(argv[1]));
	server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
	
	FD_ZERO(&read_fds);
	FD_SET(s, &read_fds);
	
	root = argv[2];
	root_len = strlen(root);
	
	if ((bind(s,(struct sockaddr*) &server_addr, sizeof(struct sockaddr))) < 0) {
		perror("Bind: ");
		return;
	}
	if (listen(s,50000)<0) {
		perror("Listen : ");
		return;
	}
}


/*
IN:		(int socket, bool close, image_tag *request, __u8 *rgb_data)
OUT:	(int socket, bool close, image_tag *request);


void jpeg_compress(__u8 *buffer, 		// input
				   int width,
				   int height,
				   JOCTET *out_buff, 	// output  LIB JPG's data type
				   int *size) 			// return value of the size of the created JPG

*/
int Compress (Compress_in *in, Compress_out *out) {
	image_tag *request = (image_tag *) in->request;
	JOCTET *out_buff = new JOCTET[(request->width*request->height*3)];
	
	int size = 0;
	
	jpeg_compress(in->rgb_data, 
				  request->width, 
				  request->height, 
				  out_buff,
				  &size); 
	
	
	out->socket = in->socket;
	out->close = in->close;
	
	out->request = in->request;
	out->request->image_data = out_buff;
	out->request->refcount++;
	in->request->size = size;
	
	delete in->rgb_data;
	return 0;
}

int Complete (Complete_in *in) {
	in->request->refcount--;
	
	if (in->close) {
		closeSocket(in->socket);
	}
	else 
		returnSocket(in->socket);
	
	return 0;
}

/*
IN:		int socket, bool close, image_tag *request
OUT:	int socket, bool close, image_tag *request
*/
int StoreInCache (StoreInCache_in *in, StoreInCache_out *out) {
//	printf("starting StoreInCache...\n");
	hash_set<image_tag *, image_tag_hash, eq_image_tag>::iterator i;
	i = cache.find(in->request);
	if (i != cache.end()) {
		free_image_tag(in->request);
		out->request = *i;
		reference_image_tag(out->request);
	} else {
		std::list<image_tag *>::iterator i;
		i = lru_q.begin();
		while (lru_q.size() >= MAX_CACHE_SIZE && i != lru_q.end()) {
//			printf("%s has %d refs\n", (*i)->name, (*i)->refcount);
			if ((*i)->refcount == 0) {
				image_tag *victim = *i;
				cache.erase(*i);
				i = lru_q.erase(i);
				free_image_tag(victim);
//				printf("Evicted a victim...\n");
			} else {
				i++;
			}
		}
		out->request = in->request;
		//cache.put(in->request);
		cache.insert(in->request);
		lru_q.push_back(out->request);
		out->request->lru_index = lru_q.end();
		out->request->lru_index--;
	}
	
	out->socket = in->socket;
	out->close = in->close;
	
//	printf("Finishing StoreInCache...\n");
	return 0;
	
}


/*
IN: (int socket, bool close, image_tag *request)
OUT: (int socket, bool close, image_tag *request, __u8 *rgb_data)
*/
int ReadInFromDisk (ReadInFromDisk_in *in, ReadInFromDisk_out *out) {
	//printf("Starting ReadInFromDisk!!!!\n");
	image_tag *request = (image_tag *)in->request;
	char *file_name = request->name;
	//printf("file_name: %s\n", file_name);
	image_t *ppm_image =  read_ppm(file_name);
	if (ppm_image == NULL)
	{
		free_image_tag(request);
		return 1;
	}
	
	out->socket = in->socket;
	out->close = in->close;
	out->request = in->request;
	out->rgb_data = ppm_image->m_data;
	
	//printf("Finishing ReadInFromDisk!!!!\n");
	return 0;
}

std::vector<Listen_out *> *listen_outs = NULL;

int Listen (Listen_out *out) {
	if (listen_outs == NULL)
	{
		printf("creating new vector...\n");
		listen_outs = new std::vector<Listen_out *>;
	}
	
	int max; 
	select_timeout.tv_sec = 0;
	select_timeout.tv_usec = 10*1000; // Half a second
	
	FD_ZERO(&read_fds);
	FD_SET(s, &read_fds);
	max = s;
	
	for (int i=0;i<1024;i++) 
	{
		if (socket_in_use[i]==0) 
		{
			if (i > max)
				max = i;
			FD_SET(i, &read_fds);
		}
	}
	
	int sel=0;
	int ix = 0;
	
	Listen_out *var;
	if (listen_outs->size() > 0)
	{
		int size = listen_outs->size();
		var = (Listen_out *)listen_outs->at(size-1);
		listen_outs->pop_back();
		out->socket = var->socket;
	}
	else
	{
		if ((sel=select(max+1, &read_fds, NULL, NULL, &select_timeout)) > 0)
		//if ((sel=select(max+1, &read_fds, NULL, NULL, NULL)) > 0)
		{
			int sock;
			if (FD_ISSET(s, &read_fds))
			{
				socklen_t length =  sizeof(struct sockaddr);
				sock = accept(s, (struct sockaddr *)&server_addr,&length);
				int optval = 1;
				if (setsockopt (sock, IPPROTO_TCP, TCP_NODELAY, &optval, sizeof (optval)) < 0)
				{
					perror("setsockopt");
				}
				var = new Listen_out();
				var->socket = sock;
				listen_outs->push_back(var);
				ix++;
				socket_in_use[sock] = 1;
			}
			for (int i=0;i<1024;i++)
			{
				if (socket_in_use[i] == 0)
				{
					if (FD_ISSET(i, &read_fds))
					{
						var = new Listen_out();
						var->socket = i;
						listen_outs->push_back(var);
						ix++;
						FD_CLR(i, &read_fds);
						socket_in_use[i] = 1;
					}
				}
			}
			int size = listen_outs->size();
			var = (Listen_out *)listen_outs->at(size-1);
			listen_outs->pop_back();
			out->socket = var->socket;
		}
		else
		{
			return -1;
		}
	}
	return 0;
}

int Handler (Handler_in *in, Handler_out *out) {

}

/*
IN:		int socket, bool close, image_tag *request 
OUT:	int socket, bool close, image_tag *request
*/
int CheckCache (CheckCache_in *in, CheckCache_out *out) {
	out->socket = in->socket;
	out->close = in->close;
	
	hash_set<image_tag *, image_tag_hash, eq_image_tag>::iterator i;
	i = cache.find(in->request);
	if (i != cache.end()) {
		//printf("image is in cache!!!!\n");
		free_image_tag(in->request);
		out->request = *i;
		reference_image_tag(out->request);
	} else {
		//printf("name: %s\n", in->request->name);
		out->request = in->request;
	}
	return 0;
}

int Write (Write_in *in, Write_out *out) {
//	printf("starting Write...\n");
	out->socket = in->socket;
	out->close = in->close;
	out->request = in->request;

	int header_len;
	
	//out->content="image/jpeg";
	char *content = "image/jpeg";
	char hdrs[8192];
	
	if (in->close) {
		sprintf(hdrs, "HTTP/1.1 200 OK\r\nContent-type: %s\r\nServer: Markov 0.1\r\nConnection: close\r\nContent-Length: %d\r\n\r\n", 
				content, in->request->size);
	} else {
		sprintf(hdrs, "HTTP/1.1 200 OK\r\nContent-type: %s\r\nServer: Markov 0.1\r\nContent-Length: %d\r\n\r\n", 
				content, in->request->size);
	}
	
	header_len = strlen(hdrs);
	int written = write(in->socket, hdrs, header_len);
	while (written < header_len) {
		if (written < 0) {
			perror("Writing");
			closeSocket(in->socket);
			return -1;
		}
		written += write(in->socket, hdrs+written, header_len-written);
	}

//	printf("Writing image: %s; width: %d, height: %d; size: %d\n", in->request->name, in->request->width, in->request->height, in->request->size);
	
	written = write(in->socket, in->request->image_data, in->request->size);
	while (written < in->request->size) {
		if (written < 0) {
			perror("Writing");
			closeSocket(in->socket);
			return -1;
		}
		written += write(in->socket, in->request->image_data + written, 
						 in->request->size - written);
	}
	
	return 0;
}


/*
IN:		int socket
OUT:	int socket, bool close, image_tag *request
*/
int ReadRequest (ReadRequest_in *in, ReadRequest_out *out) {
	char buf[BUFFER_SIZE];
	int rd;
	int length = 0; 
	int doneRequest = 0;
	
	out->request = 0;
	out->close = false;
	
	char *curr_request;
//DEBUG printf("ReadRequest in\n");
	
	//printf("inside of readrequest \n");
	do 
	{
		rd = read(in->socket, buf+length, BUFFER_SIZE-length);
		if (rd == -1) 
		{
			perror("Reading request");
			closeSocket(in->socket);
			return -1;
		} 
		else if (!rd) 
		{
			usleep(1000*10);
		}
		else 
		{
			char *start = buf;
			char *end;
			for (int i=0;i<rd;i++) 
			{
				if (buf[i] == '\r') 
				{
					buf[i++] = 0;
					buf[i] = 0; // Get rid of the \n
					
					//DEBUG printf("%s\n", start);
					if (length == 0) 
					{ // We're done...
						doneRequest = true;
						break;
					}
					else if (start[0] == 'G' && start[1] == 'E' && start[2] == 'T') 
					{
						start = strchr(start, ' ')+1;
						end = strchr(start+1, ' ');
						*end = 0;
						while (*end != 'H')
							end++;
						
						while (*end != '/')
							end++;
						
						end++;
						int major = *end-'0'; // HACK HACK HACK Assumes ASCII
						end+=2;
						int minor = *end-'0'; // HACK HACK HACK Assumes ASCII
						
						if (major != 1 || (minor > 1))
							printf("Urm, HTTP version: %d.%d we may be in trouble...\n", 
								   major, minor);
						
						if (major == 1 && minor == 0) 
						{
							out->close = true;
						}
						
						if (*start == '/')
							start++;
						//out->request = strdup(start);
						curr_request = strdup(start);
					}
					else if (start[0] == 'C' && start[1] == 'o' && start[2] == 'n' &&
											strstr(start, "close")) 
					{
						out->close = true;
					}
					start=buf+i+1;
					length = 0;
				}
				else 
				{
					length++;
				}
			}
			if (!doneRequest) 
			{
				strncpy(buf, start, length);
			}
		}
	} while (!doneRequest);
	
	//printf("curr_request: %s\n", curr_request);
	//if (!out->request) {
	if (!curr_request)
	{
		//out->request = "/sys_error.html";
		
		// ERROR OUT
		return 1;
	}
	else
	{
		
		int width = 0;
		int height = 0;
		char name[256];
		
		if(!parse_request(curr_request, name, &width, &height ))
		{
			printf("bad request!!!\n");
			return 1;
		}
		
		//request->name = name;
		out->request = new image_tag;
		//request->name = new char[256];
		
		//strcpy(request->name, name);
		out->request->name = strdup(name);
		out->request->width = width;
		out->request->height = height;
		out->request->refcount = 0;
		out->request->size = 0;
		out->request->image_data = NULL;
		
		//out->request = request;
		
//		printf("request: %s\n", out->request->name);
		
		// TODO create a 
	}
	out->socket = in->socket;
//	printf("done wirth ReadRequest\n");
// DEBUG printf("Request:%s:\n", out->request);
	return 0;
}


// request format:
// /<image name>.jpg?width=<width>height=<height>



int Page (Page_in *in) {
	
}

bool TestInCache(image_tag* request) {
	return request->image_data != NULL;
}

void writeHeaders(int socket_in, bool close, int length, char *content) {
	char msg[128];
	
	sprintf(msg, "Content-Length: %d\r\n", length);
	write(socket_in, msg, strlen(msg));
	sprintf(msg, "Server: Markov 0.1\r\n");
	write(socket_in, msg, strlen(msg));
	sprintf(msg, "Content-Type: %s\r\n", content);
	write(socket_in, msg, strlen(msg));
	if (close)
		write(socket_in, "Connection: close\r\n",19); 
	write(socket_in, "\r\n", 2);
}


void FourOhFor(ReadInFromDisk_in *in, int err) {
	char *msg = "HTTP/1.1 404 File not found\r\n";
	write(in->socket, msg, strlen(msg));
	msg = "<html><body><h2>404 File Not Found!</h2></body></html>\n";
	writeHeaders(in->socket, in->close, strlen(msg), "text/html");
	write(in->socket, msg, strlen(msg));
	if (in->close)
		closeSocket(in->socket);
	else
		returnSocket(in->socket);
}


int SessionId(Page_in *in) {
	return in->socket;
}
