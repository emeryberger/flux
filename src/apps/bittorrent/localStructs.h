#ifndef __M_LOCALSTRUCTS__
#define __M_LOCALSTRUCTS__

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <openssl/sha.h>
#include <netdb.h>
#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <poll.h>

#include <list>
#include <map>
#include <string>
#include <queue>

typedef struct
{
    int length;
    char *data;
} message_t;

typedef struct
{
    unsigned int piece;
    unsigned int offset;
    unsigned int length;
    bool received;
} chunk_t;

typedef struct
{
    bool choked;
    bool choking;
    bool interesting;
    bool interested;
    bool listenable;
    int socket;
    int upload_speed;
    unsigned char *bit_field;

    pthread_mutex_t *client_lock;
    pthread_mutex_t *sendmsg_lock;
} client_data_t;

typedef struct
{
    std::vector<client_data_t *> choke;
    std::vector<client_data_t *> unchoke;
} chokelist_t;

typedef struct 
{
    char *file_name;
    char *info_hash;
    int file_size;
    int pieces;
    unsigned char **piece_hashes;
    char *peer_id;
    
    unsigned char *bit_field;
    std::list <chunk_t> chunks;

    enum status_t 
    {
        DownloadPending = 0, // We have started downloading, haven't informed the tracker yet.
        Downloading = 1,  
        CompletePending = 2, 
        Completed = 3
    };
    enum status_t satus;
    
    int IP;     // *Our* IP address
    int PORT;   // The port we're listening on
    int listen_sock;
    
    int uploaded;
    int downloaded;
    int remaining;
    
    int piece_size;
    int fd;     // For the file we're uploading/downloading
    char *file; // for mmap'ing it
    char *trackerURL; // URL of the tracker
    int tracker_port;
    char *tracker_host;
    char *tracker_path;
    int interval;

    std::list<client_data_t *> clients;
} torrent_data_t;

#endif //__M_LOCALSTRUCTS__
