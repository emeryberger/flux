#ifndef __M_IMPL__
#define __M_IMPL__
///////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////
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
//#include "mImpl.h"

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
    
    // Lock using bitmap_lock
    unsigned char *bit_field;
    
    // Lock using chunk_lock
    std::list <chunk_t> chunks;

    // Lock using status_lock    
    enum status_t 
    {
        DownloadPending = 0, // We have started downloading, haven't informed the tracker yet.
        Downloading = 1,  
        CompletePending = 2, 
        Completed = 3
    };
    enum status_t satus;
    
    // Never change after init; don't need to lock
    int IP;     // *Our* IP address
    int PORT;   // The port we're listening on
    int listen_sock;
    
    // Total number of bytes transferred/remaining
    // Lock using udr_lock
    int uploaded;
    int downloaded;
    int remaining;
    
    // These never change after init; don't need to lock
    int piece_size;
    int fd;     // For the file we're uploading/downloading
    char *file; // for mmap'ing it
    char *trackerURL; // URL of the tracker
    int tracker_port;
    char *tracker_host;
    char *tracker_path;
    int interval;

    // List of currently connected clients
    // Lock using client_list_lock
    std::list<client_data_t *> clients;

    // Locks must be acquired in the order listed below    
    pthread_mutex_t *udr_lock;
    pthread_rwlock_t *clientlist_lock;
    
    pthread_rwlock_t *chunk_lock;
    pthread_rwlock_t *bitmap_lock;
    pthread_mutex_t *status_lock;
    
} torrent_data_t;
///////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////
#include "mStructs.h"
#include <vector>
void init(int argc, char **argv);
int ReadMessage (ReadMessage_in *in, ReadMessage_out *out);
int CompletePiece (CompletePiece_in *in, CompletePiece_out *out);
int Handshake (Handshake_in *in, Handshake_out *out);
int Piece (Piece_in *in, Piece_out *out);
int Listen (Listen_out *out);
int TrackerTimer (TrackerTimer_out *out);
int VerifyPiece (VerifyPiece_in *in, VerifyPiece_out *out);
int Message (Message_in *in);
int Connect (Connect_out *out);
int SendKeepAlives (SendKeepAlives_in *in);
int KeepAliveTimer (KeepAliveTimer_out *out);
int Interested (Interested_in *in, Interested_out *out);
int MessageDone (MessageDone_in *in);
int Bitfield (Bitfield_in *in, Bitfield_out *out);
int UpdateChokeList (UpdateChokeList_in *in);
int Choke (Choke_in *in, Choke_out *out);
int SelectSockets (SelectSockets_in *in, SelectSockets_out *out);
int HandleMessage (HandleMessage_in *in, HandleMessage_out *out);
int Cancel (Cancel_in *in, Cancel_out *out);
int CheckSockets (CheckSockets_in *in, CheckSockets_out *out);
int PickChoked (PickChoked_in *in, PickChoked_out *out);
int SendUninterested (SendUninterested_in *in, SendUninterested_out *out);
int ChokeTimer (ChokeTimer_out *out);
int GetClients (GetClients_out *out);
int SendChokeUnchoke (SendChokeUnchoke_in *in);
int Unchoke (Unchoke_in *in, Unchoke_out *out);
int SendBitfield (SendBitfield_in *in);
int Have (Have_in *in, Have_out *out);
int CheckinWithTracker (CheckinWithTracker_in *in);
int Uninterested (Uninterested_in *in, Uninterested_out *out);
int SendRequestToTracker (SendRequestToTracker_in *in, SendRequestToTracker_out *out);
int SetupConnection (SetupConnection_in *in);
int SendHave (SendHave_in *in, SendHave_out *out);
int SendRequest (SendRequest_in *in, SendRequest_out *out);
int GetTrackerResponse (GetTrackerResponse_in *in);
int Request (Request_in *in, Request_out *out);
bool TestChoke(int type);
bool TestUnchoke(int type);
bool TestInterested(int type);
bool TestUninterested(int type);
bool TestRequest(int type);
bool TestCancel(int type);
bool TestPiece(int type);
bool TestPieceComplete(int piece);
bool TestBitfield(int type);
bool TestHave(int type);
#endif //__M_IMPL__
