#include "mImpl.h"

#include <queue>

#define MSG_CHOKE          0
#define MSG_UNCHOKE        1
#define MSG_INTERESTED     2
#define MSG_NOTINTERESTED  3
#define MSG_HAVE           4
#define MSG_BITFIELD       5 
#define MSG_REQUEST        6
#define MSG_PIECE          7
#define MSG_CANCEL         8

#define MSG_CHOKE_LENGTH          1
#define MSG_UNCHOKE_LENGTH        1
#define MSG_INTERESTED_LENGTH     1
#define MSG_NOTINTERESTED_LENGTH  1
#define MSG_HAVE_LENGTH           5
#define MSG_BITFIELD_HDR_LENGTH   1 
#define MSG_REQUEST_LENGTH        13
#define MSG_PIECE_HDR_LENGTH      9
#define MSG_CANCEL_LENGTH         13


#define PEERID_LENGTH      20
#define ESCSEQ_LENGTH      3
#define TRACKER_BUFSIZE    8192

#define MIN_LISTEN_PORT    6881
#define MAX_LISTEN_PORT    6889

#define TCP_CONNQ_LEN      10

#define HANDSHAKE_RESERVED_LENGTH 8
#define BT_PROT_ID         "BitTorrent protocol"
#define HTTP10_OK          "HTTP/1.0 200 OK\r\n"
#define DEFAULT_HTTP_PORT  80

#define KEEPALIVE_INTERVAL 105
#define CHOKE_INTERVAL     10

const unsigned char BIT_HEX[] = {0x80,0x40,0x20,0x10,0x08,0x04,0x02,0x01};
const char HEX_VALUES[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 
    'A', 'B', 'C', 'D', 'E', 'F'};
const char *MESSAGE_TYPES[] = { "choke", "unchoke", "interested", 
    "not interested", "have", "bitfield", "request", "piece", "cancel"};

torrent_data_t tdata;
std::queue<client_data_t *> incomingmsgq;

int item_length(const char *item)
{
	const char *p = item;
	if (*p == 'i') {
		while (*p != 'e') p++;
	} else if (*p >= '0' && *p < '9') {
		int slen = atoi(p);
		while (*p != ':') p++;
		p += slen;
	} else if (*p == 'l' || *p == 'd') {
		p++;
		while (*p != 'e') p += item_length(p);
	} else {
		fprintf(stderr, "item_length: Error decoding, unexpected type\n");
		exit(0);
	}
	
	return p - item + 1;
}

int decode_int(const char *intval)
{
	int i;
	
	if (*intval != 'i') {
		fprintf(stderr, "decode_int: Error decoding, not given an int\n");
		exit(0);
	}
	
	intval++;
	i = atoi(intval);
	
	return i;
}

// Note that it is the caller's responsibility to delete the returned string
char *decode_string(const char *str, int *len)
{
	int slen;
	char *s;
	
	slen = atoi(str);
	if (len) *len = slen;
	s = new char[slen + 1];
	while (*str != ':') str++;
	str++;
	memcpy(s, str, slen);
	s[slen] = 0;
	return s;
}

// Return a map of string keys to bencoded values
std::map<std::string, const char *> decode_dict(const char *dict)
{
	std::map<std::string, const char *> d;

	if (*dict != 'd') {
		fprintf(stderr, "decode_dict: Error decoding, not given a dictionary\n");
		exit(0);
	}
	
	dict++;
	
	while (*dict != 'e') {
		std::string key;
		int slen = atoi(dict);
		while (*dict != ':') dict++;
		dict++;
		key.assign(dict, 0, slen);
		dict += slen;
		d[key] = dict;
		dict += item_length(dict);
	}
	
	return d;
}

std::vector<const char *> decode_list(const char *list)
{
	std::vector<const char *> lst;
	
	if (*list != 'l') {
		fprintf(stderr, "decode_list: Error decoding, not given a list\n");
		exit(0);
	}
	
	list++;

	while (*list != 'e') {
		lst.push_back(list);
		list += item_length(list);
	}
	
	return lst;
}

// Note, this function should probably be cleaned up
// It was stolen from ctorrent

int http_url_analyze(char *url,char *host,int *port,char *path)
{
    char *p;
    int r;
    *port = DEFAULT_HTTP_PORT;
    p = strstr(url,"://");
    if( !p ) 
        p = url;
    else
        p += 3; // 3 == strlen("://");

    /* host */
    for(; *p && (isalnum(*p) || *p == '.' || *p == '-'); p++, host++) 
        *host = *p;
    *host = '\0';

    if( *p == ':' ){
        /* port */
        p++;
        for( r = 0; p[r] >= '0' && p[r] <= '9' && r < 6; r++) ;

        if( !r ) return -1;
        *port = atoi(p);
        if(*port > 65536) return -1;
        p += r;
    }

    /* path */
    if( *p != '/' ) return -1;
    for( ; *p && *p != '?'; p++,path++) *path = *p;
    *path = '\0';
    return 0;
}

/* bool parseTorrent(char *torrentfile)
 *
 * Parses the file named by torrentfile, and fills in the file_name, 
 * info_hash, file_size, piece_size, pieces, piece_hashes, trackerURL,
 * tracker_port, tracker_host, and tracker_path fields of the tdata 
 * structure.
 */

bool parse_torrent(char *torrentfile)
{
	int tfd;
	char *data;
	const char *hashes;
	int len;
	
	if ((tfd = open(torrentfile, O_RDONLY)) < 0) {
		perror("open: ");
		return false;
	}
    
	struct stat fs;
    if (fstat(tfd, &fs) < 0) {
        perror("fstat: ");
		return false;
    }
	
	if (!(data = new char[fs.st_size])) {
        fprintf(stderr, "parse_torrent: Error allocating memory\n");
		return false;
	}
	
	int total_read = 0, bytes_read = 1;
	while (total_read < fs.st_size && bytes_read > 0)
		total_read += bytes_read = read(tfd, data + total_read, fs.st_size - total_read);
    if (total_read != fs.st_size || bytes_read < 0) {
        perror("read: ");
		delete [] data;
		return false;
    }
	
	close(tfd);
	
	std::map<std::string, const char *> tfile = decode_dict(data);
	if (tfile.find("announce") == tfile.end()
			|| tfile.find("info") == tfile.end()) {
		fprintf(stderr, "parse_torrent: Missing keys in torrent file\n");
		delete [] data;
		return false;
	}
	
	tdata.trackerURL = decode_string(tfile["announce"], NULL);
    
	tdata.info_hash = new char[SHA_DIGEST_LENGTH];
    SHA1((const unsigned char *) tfile["info"], item_length(tfile["info"]), 
		(unsigned char *) tdata.info_hash);
	
	std::map<std::string, const char *> tinfo = decode_dict(tfile["info"]);
	if (tinfo.find("files") != tinfo.end()) {
		fprintf(stderr, "parse_torrent: This client does not support multiple files in a single torrent\n");
		delete [] data;
		return false;
	}

	if (tinfo.find("name") == tinfo.end()
			|| tinfo.find("piece length") == tinfo.end()
			|| tinfo.find("pieces") == tinfo.end()
			|| tinfo.find("length") == tinfo.end()) {
		fprintf(stderr, "parse_torrent: Missing keys in torrent file\n");
		delete [] data;
		return false;
	}

	tdata.file_name = decode_string(tinfo["name"], NULL);
	tdata.file_size = decode_int(tinfo["length"]);
	tdata.piece_size = decode_int(tinfo["piece length"]);
	tdata.pieces = (tdata.file_size + tdata.piece_size - 1) / tdata.piece_size;
	
	hashes = decode_string(tinfo["pieces"], &len);
	if (len != tdata.pieces * SHA_DIGEST_LENGTH) {
		fprintf(stderr, "parse_torrent: Number of piece hashes doesn't match file size\n");
		delete [] data;
		delete [] hashes;
		return false;
	}
	
	tdata.piece_hashes = new unsigned char *[tdata.pieces];
	for (int i = 0; i < tdata.pieces; i++)
	{
		tdata.piece_hashes[i] = new unsigned char[SHA_DIGEST_LENGTH];
		memcpy(tdata.piece_hashes[i], hashes + i * SHA_DIGEST_LENGTH, SHA_DIGEST_LENGTH);
	}
	
	delete [] hashes;
	
	tdata.tracker_host = new char[strlen(tdata.trackerURL)];
	tdata.tracker_path = new char[strlen(tdata.trackerURL)];
	if (http_url_analyze(tdata.trackerURL, tdata.tracker_host, 
			&tdata.tracker_port, tdata.tracker_path) != 0) {
		fprintf(stderr, "http_url_analyze: Error parsing tracker URL\n");
		return false;
	}
	
	printf("Tracker URL: %s\n", tdata.trackerURL);
	printf("Name: %s\n", tdata.file_name);
	printf("File length: %d\n", tdata.file_size);
	printf("Piece length: %d\n", tdata.piece_size);
	printf("Total pieces: %d\n", tdata.pieces);
	
	delete [] data;
	
	return true;
}

void print_hex(unsigned char *data, int len)
{
    for (int i = 0; i < len; i++)
        printf("%02x", (int) data[i]);
}

void print_sha1(unsigned char *sha1)
{
    print_hex(sha1, SHA_DIGEST_LENGTH);
}

void delete_client(torrent_data_t *tdata, client_data_t *client_to_delete)
{
    printf("Killing client on socket %d\n", client_to_delete->socket);
	
    tdata->clients.remove (client_to_delete);
    
    // This was a bad idea... need to make sure other people aren't 
    // using the client before we delete it
/*    close(client_to_delete->socket);
    delete client_to_delete;*/
}

void escape_string(unsigned char *dest, const unsigned char *src, int str_len) {
    for (int i = 0; i < str_len; i++) {
		if ((*src >= '0' && *src <= '9') || (*src >= 'a' && *src <= 'z')
			|| (*src >= 'A' && *src <= 'Z') || *src == '$' || *src == '-' 
			|| *src == '_' || *src == '.' || *src == '+' || *src == '!'
			|| *src == '*' || *src == '(' || *src == ')') {
			*dest++ = *src;
		} else {
            *(dest++) = '%';
            *(dest++) = HEX_VALUES[(*src) / 16];
            *(dest++) = HEX_VALUES[(*src) % 16];
		}
		src++;
	}
	
	*dest = 0;
}

bool send_tracker_request(torrent_data_t *tdata, int *sock, const char *event) {
	char path[TRACKER_BUFSIZE];
	
  
	struct sockaddr_in tr_addr;
    struct sockaddr_in cl_addr;
	tr_addr.sin_family = AF_INET;
	tr_addr.sin_port = htons(tdata->tracker_port);
    
    cl_addr.sin_family = AF_INET;
    cl_addr.sin_port = INADDR_ANY;
    cl_addr.sin_addr.s_addr = INADDR_ANY;
  
    if (inet_aton(tdata->tracker_host, &(tr_addr.sin_addr)) == 0) {
    	struct hostent *ph = gethostbyname(tdata->tracker_host);
    	if (!ph || ph->h_addrtype != AF_INET)
			return false;
    	memcpy(&tr_addr.sin_addr, ph->h_addr_list[0], sizeof(struct in_addr));
    }
	
    if ((*sock = socket(AF_INET, SOCK_STREAM, 0)) == -1) {
		perror("socket: ");
		return false;
	}
    
    if (bind(*sock, (struct sockaddr *) &cl_addr, sizeof(cl_addr)) == -1) {
        perror("bind: ");
        return false;
    }
	
	if (connect(*sock, (struct sockaddr *) &tr_addr, sizeof(tr_addr)) == -1) {
		perror("connect: ");
		return false;
	}
	
	char esc_info_hash[SHA_DIGEST_LENGTH * ESCSEQ_LENGTH + 1];
	char esc_peer_id[PEERID_LENGTH * ESCSEQ_LENGTH + 1];
	char request[TRACKER_BUFSIZE];
    escape_string((unsigned char *) esc_info_hash, (unsigned char *) tdata->info_hash, SHA_DIGEST_LENGTH);
    escape_string((unsigned char *) esc_peer_id, (unsigned char *) tdata->peer_id, PEERID_LENGTH);
    printf("Info hash is: %s\n", esc_info_hash);
    printf("Peer id is: %s\n", esc_peer_id);
    sprintf(request, "GET %s?info_hash=%s&peer_id=%s&port=%d&uploaded=0&downloaded=0&left=%d"
            "&compact=1%s%s HTTP/1.0\r\nUser-agent: MarkovBitTorrent\r\n\r\n", 
        tdata->tracker_path, esc_info_hash, esc_peer_id, tdata->PORT, tdata->remaining, 
        event ? "&event=" : "", event ? event : "");
		
	int len = strlen(request);
	int total_written = 0, bytes_written = 0;
	while (total_written < len && bytes_written >= 0)
		total_written += bytes_written = write(*sock, request + total_written, len - total_written);
	
	if (bytes_written < 0) {
		fprintf(stderr, "send_tracker_response: Error writing to socket!");
		close(*sock);
		return false;
	}
	
	return true;
}

bool get_tracker_response(torrent_data_t *tdata, int sock) 
{
	char response[TRACKER_BUFSIZE];
    char *p;
	int total_read = 0, bytes_read = 1;
	while (bytes_read > 0)
		total_read += bytes_read = read(sock, response + total_read, TRACKER_BUFSIZE);
		
	if (bytes_read < 0) {
		fprintf(stderr, "get_tracker_response: Error reading from socket!");
        // I'm not entirely sure what we should do here...
        // Dying 
		close(sock);
		return false;
	}
    
    close(sock);
	
	response[total_read] = 0;
    
    if (strncmp(response, HTTP10_OK, strlen(HTTP10_OK)) != 0) {
        printf("get_tracker_response: bad response\n%s\n", total_read, response);
        return false;     
    }
	
    // TODO Properly parse the response instead of just cracking right through the
    // headers to get to the soft chewy bencoded inside
    p = response + strlen(HTTP10_OK);
    while (p[0] != '\r') {
        p = strchr(p, '\r');
        p += 2; // skip \r and \n
    }
    
    p += 2; // Skip the final \r and \n
    
    std::map<std::string, const char *> tres = decode_dict(p);
    if (tres.find("interval") == tres.end()) {
        fprintf(stderr, "get_tracker_response: Missing keys in tracker response\n");
        return false;
    }	
    
    tdata->interval = decode_int(tres["interval"]);
            
	return true;
}

bool send_message (torrent_data_t *tdata, client_data_t *client, 
            const char *hdrdata, int hdrlength, const char *payload, 
            int payloadlength) {
    int total_written, bytes_written;
    
    uint32_t mlen = htonl(hdrlength + payloadlength);
    if (write(client->socket, &(mlen), sizeof(mlen)) != sizeof(mlen)) {
        fprintf(stderr, "SendMessage: Error writing message length!\n");
        delete_client(tdata, client);
        return 1;
    }
    
    total_written = 0;
    bytes_written = 0;
    
    while (total_written < hdrlength && bytes_written >= 0)
        total_written += bytes_written = write(client->socket, 
            hdrdata + total_written, hdrlength - total_written);
    
    if (bytes_written < 0) {
        perror("write: ");
        delete_client(tdata, client);
        return 1;
    }
    
    total_written = 0;
    bytes_written = 0;
    
    while (total_written < payloadlength && bytes_written >= 0)
        total_written += bytes_written = write(client->socket, 
            payload + total_written, payloadlength - total_written);
    
    if (bytes_written < 0) {
        perror("write: ");
        delete_client(tdata, client);
        return 1;
    }
    
    return 0;
}

void send_interested(torrent_data_t *tdata, client_data_t *client) {
    static char data[] = {MSG_INTERESTED};
    send_message(tdata, client, data, MSG_INTERESTED_LENGTH, NULL, 0);
}

/* void init(int argc, char **argv)
 *
 * Initializes the tdata data structure.
 * Reads and parses the torrent file, then opens and mmaps the download file, 
 * if it exists already, otherwise creates it. Then SHA1's chunks of the file
 * to see what of the parts that we have are correct. Then starts listening on 
 * a socket for incoming requests from clients and makes initial contact with
 * the tracker.
 */
 
void init(int argc, char **argv) {
    signal(SIGPIPE, SIG_IGN);
    
    if (argc != 2) {
        fprintf(stderr, "Usage: mbittorrent <torrent>\n");
        exit(1);
    }
    
    tdata.file_name = NULL;
    tdata.uploaded = 0;
    tdata.downloaded = 0;
    tdata.info_hash = NULL;
	tdata.peer_id = new char[PEERID_LENGTH + 1];
	
	// Start off the peer id with a client identifier
    sprintf(tdata.peer_id, "M4-0-0--");
	// And fill the rest with random hex values
	char *p = tdata.peer_id + strlen(tdata.peer_id);
	for (; p - tdata.peer_id < PEERID_LENGTH; p++)
		*p = HEX_VALUES[rand() % 16]; 
	*p = 0;
    
    // Read in the actual torrent file
    if (!parse_torrent(argv[1])) exit(1);
    
    // Allocate the bitfield
    tdata.bit_field = new unsigned char[(tdata.pieces + 7) / 8];
    bzero(tdata.bit_field, (tdata.pieces + 7) / 8);
    
    // Open, stat, and mmap the file
    if ((tdata.fd = open(tdata.file_name, O_RDWR | O_CREAT)) < 0) {
        perror("open: ");
        exit(1);
    }
    
    struct stat fs;
    if (fstat(tdata.fd, &fs) < 0) {
        perror("fstat: ");
        exit(1);   
    }
    
    if ((tdata.file = (char *) mmap(0, tdata.file_size, PROT_READ | PROT_WRITE, 
            MAP_SHARED, tdata.fd, 0)) == NULL) {
        perror("mmap: "); 
        exit(1);
    }
    
    // Hash the pieces that we have to see which ones are valid
    unsigned char piece_sha1[SHA_DIGEST_LENGTH];
	int pieces_dled = 0;
    tdata.remaining = tdata.file_size;
    for (int i = 0; i < fs.st_size / tdata.piece_size; i++) {
        SHA1((unsigned char *) tdata.file + tdata.piece_size * i, 
			tdata.piece_size, piece_sha1);
        if (memcmp(piece_sha1, tdata.piece_hashes[i], SHA_DIGEST_LENGTH) == 0) {
			pieces_dled++;
            tdata.remaining -= tdata.piece_size;
            tdata.bit_field[i / 8] |= BIT_HEX[i % 8];
		}
    }
	
	// Check if we have the last bit of it
	if (fs.st_size == tdata.file_size && tdata.file_size % tdata.piece_size != 0) {
        SHA1((unsigned char *) tdata.file + tdata.file_size - tdata.file_size 
			% tdata.piece_size, tdata.file_size % tdata.piece_size, piece_sha1);
        if (memcmp(piece_sha1, tdata.piece_hashes[tdata.pieces - 1], SHA_DIGEST_LENGTH) == 0) {
			pieces_dled++;
            tdata.remaining -= tdata.file_size % tdata.piece_size;
            tdata.bit_field[(tdata.pieces - 1) / 8] |= BIT_HEX[(tdata.pieces - 1) % 8];
		}		
	}
	
	printf("Client has %d of %d pieces\n", pieces_dled, tdata.pieces);
    
    // Set up the socket to listen on
    tdata.listen_sock = socket(AF_INET, SOCK_STREAM, 0);
    int val = 1;
    if (setsockopt(tdata.listen_sock, SOL_SOCKET, SO_REUSEADDR, &val, sizeof(val)) < 0) {
        perror("setsockopt");
        exit(1);
    }
 
	tdata.PORT = MIN_LISTEN_PORT;
    struct sockaddr_in server_addr;
    
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    server_addr.sin_port = htons(tdata.PORT);
	
    while (tdata.PORT <= MAX_LISTEN_PORT && bind(tdata.listen_sock, 
           (struct sockaddr*) &server_addr, sizeof(struct sockaddr)) < 0) {
        tdata.PORT++;               
        server_addr.sin_port = htons(tdata.PORT);
	}
    
	if (tdata.PORT > MAX_LISTEN_PORT) {
		perror("bind: ");
		exit(1);
	}
    
    if (listen(tdata.listen_sock, TCP_CONNQ_LEN) < 0) {
        perror("listen: ");
        exit(1);
    }
    
    fprintf (stderr, "listening on address %s, port %d\n",
             server_addr.sin_addr.s_addr, tdata.PORT);
			 
	int tsock;
    
	if (!send_tracker_request(&tdata, &tsock, "started") 
			|| !get_tracker_response(&tdata, tsock))
		exit(0);
    
    printf("Done with init!\n");
}

int Bitfield (Bitfield_in *in, Bitfield_out *out) {
	out->client = in->client;
    
    // now parse the message
	if (in->length != (in->tdata->pieces + 7) / 8) {
		fprintf(stderr, "Bitfield: Client sent wrong size bitfield\n");
		return 0;
	}
	
    // check if this client has anything that we need:
	delete [] in->client->bit_field;
    in->client->bit_field = (unsigned char *) in->payload;
	
	for (int i = 0; i < (in->tdata->pieces + 7) / 8; i++)
    	if (in->client->bit_field[i] & ~in->tdata->bit_field[i])
        	in->client->interesting = true;
	
	if (in->client->interesting)
		send_interested(in->tdata, in->client);
    
	return 0;
}

int Message (Message_in *in) {
	return 0;
}

int CheckinWithTracker (CheckinWithTracker_in *in) {
	return 0;
}

int GetTrackerResponse (GetTrackerResponse_in *in) {
    get_tracker_response(in->tdata, in->socket);
    return 0;
}

int CompletePiece (CompletePiece_in *in, CompletePiece_out *out) {
	return 0;
}

int Have (Have_in *in, Have_out *out) {
	out->tdata = in->tdata;
	out->client = in->client;
	
    int piece = ntohl(*((int *) in->payload));
	delete [] in->payload;
    
    // now parse the message
	if (piece < 0 || piece > in->tdata->pieces) {
		fprintf(stderr, "Have: Client sent have message with invalid piece number\n");
		return 0;
	}

    // check if this client has anything that we need:
	in->client->bit_field[piece / 8] |= BIT_HEX[piece % 8];
	
	if (!in->client->interesting 
            && (in->client->bit_field[piece / 8] & ~in->tdata->bit_field[piece / 8])) {
		in->client->interesting = true;
		send_interested(in->tdata, in->client);
	}
	
    return 0;

}

int Piece (Piece_in *in, Piece_out *out) {
	out->tdata = in->tdata;
	out->client = in->client;
	out->piece = -1;
	
	delete [] in->payload;
	
	return 0;
}

int SendKeepAlives (SendKeepAlives_in *in) {
	std::list<client_data_t *>::iterator i;
	for (i = in->tdata->clients.begin(); i != in->tdata->clients.end(); i++)
        send_message(in->tdata, *i, NULL, 0, NULL, 0);
	
	return 0;
}

int Choke (Choke_in *in, Choke_out *out) {
	out->client = in->client;

    in->client->choked = true;
    
	return 0;    
}

int SetupConnection (SetupConnection_in *in) {
	return 0;
}

int Interested (Interested_in *in, Interested_out *out) {
	out->client = in->client;
    
    in->client->interested = true;
    return 0;    
}

int SendHave (SendHave_in *in, SendHave_out *out) {
	out->tdata = in->tdata;
	out->client = in->client;
	
	return 0;

}

int VerifyPiece (VerifyPiece_in *in, VerifyPiece_out *out) {
	out->tdata = in->tdata;
	out->client = in->client;
	
	return 0;
	
}

int SendRequest (SendRequest_in *in, SendRequest_out *out) {
	out->client = in->client;
	
	return 0;
}

int Handshake (Handshake_in *in, Handshake_out *out) {
    
//    printf("Attempting handshake on socket %d\n", in->socket);
    
	out->tdata = in->tdata;

	client_data_t *client = new client_data_t;
	out->client = client;
	
	client->choked = true;
	client->choking = false;  // NOTE THIS IS FOR BENCHMARKING PURPOSES ONLY
	client->interested = false;  // kevin, yoou sooooo crazzzy ~ alex "i is"~kevin
	client->interesting = false;
	client->listenable = true;
	client->socket = in->socket;
	client->upload_speed = 0;
	client->bit_field = new unsigned  char[(in->tdata->pieces + 7) / 8];
	bzero(client->bit_field, (in->tdata->pieces + 7) / 8);
	
    char msg[1 + strlen(BT_PROT_ID) + SHA_DIGEST_LENGTH + PEERID_LENGTH];
    msg[0] = strlen(BT_PROT_ID);
	sprintf(msg + 1, BT_PROT_ID);
	for (int i = 20; i < 28; i++)
		msg[i] = 0;
	memcpy(msg + 28, in->tdata->info_hash, 20);
	memcpy(msg + 48, in->tdata->peer_id, 20);
	
	int total_written = 0, bytes_written = 1;
	while (total_written < 68 && bytes_written >= 0)
		total_written += bytes_written = write(client->socket, msg + total_written, 68 - total_written);
	
	if (bytes_written < 0) {
		fprintf(stderr, "Handshake: Error writing to socket!");
        delete_client(in->tdata, client);
        return 1;
	}
	
	if (read(client->socket, msg, 1) != 1) {
		fprintf(stderr, "Handshake: Error reading protocol length from socket!");
        delete_client(in->tdata, client);
        return 1;
	}
	
	if (msg[0] != 19) {
		fprintf(stderr, "Handshake: Unrecognized protocol!");
        delete_client(in->tdata, client);
		return 1;
	}
	
	int total_read = 0, bytes_read = 1;
	while (total_read < 19 && bytes_read > 0)
		total_read += bytes_read = read(client->socket, msg + 1 + total_read, 19 - total_read);
	if (bytes_read < 0) {
		fprintf(stderr, "Handshake: Error reading protocol version from socket!");
        delete_client(in->tdata, client);
        return 1;
	}
	
	if (strncmp(msg + 1, "BitTorrent protocol", 19) != 0) {
		fprintf(stderr, "Handshake: Unrecognized protocol!");
        delete_client(in->tdata, client);
        return 1;
	}
	
	total_read = 0;
	bytes_read = 1;
	while (total_read < 48 && bytes_read > 0)
		total_read += bytes_read = read(client->socket, msg + 20 + total_read, 48 - total_read);
	if (bytes_read < 0) {
		fprintf(stderr, "Handshake: Error reading from socket!");
        delete_client(in->tdata, client);
		return 1;
	}
    
    if (total_read != 48) {
        fprintf(stderr, "Handshake: Client did not send full handshake!\n");
        delete_client(in->tdata, client);
        return 1;
    }
    
    char peerid[21];
    memcpy(peerid, msg + 48, 20);
    peerid[20] = 0;
//    printf("Peer connected, id: %s\n", peerid);

	if (memcmp(msg + 28, in->tdata->info_hash, 20) != 0) {
		fprintf(stderr, "Handshake: Client attempted connecting with wrong info hash!");
        delete_client(in->tdata, client);
		return 1;
	}
    
//    printf("Successfully handshook with client on %d\n", client->socket);
	
	return 0;
}

int SendUninterested (SendUninterested_in *in, SendUninterested_out *out) {
	out->tdata = in->tdata;
	out->client = in->client;

	return 0;
}

int SendRequestToTracker (SendRequestToTracker_in *in, SendRequestToTracker_out *out) {
    out->tdata = in->tdata;
    
    char *event = NULL;
    
    // TODO Check what we have to send for event notification 
    return send_tracker_request(in->tdata, &out->socket, event) ? 0 : -1;
}

int MessageDone (MessageDone_in *in) {
	return 0;
}

int Cancel (Cancel_in *in, Cancel_out *out) {
	out->client = in->client;
	
	return 0;
}

int UpdateChokeList (UpdateChokeList_in *in) {
	return 0;
}

int Request (Request_in *in, Request_out *out) {
	out->client = in->client;
	
    int piece = ntohl(*((int *) in->payload));
    int begin = ntohl(*((int *) (in->payload + 4)));
    int length = ntohl(*((int *) (in->payload + 8)));
	
	delete [] in->payload;
	
	if (piece < 0 || piece > in->tdata->pieces
			|| begin < 0 || begin >= in->tdata->piece_size
            || begin + length > in->tdata->piece_size
            || piece * in->tdata->piece_size + begin + length > in->tdata->file_size
			|| length <= 0 || length > (1 << 14)) {
		fprintf(stderr, "Request: Client made an invalid request\n");
        delete_client(in->tdata, in->client);
		return 1;
	}
	
	if (!(in->tdata->bit_field[piece / 8] & BIT_HEX[piece % 8])) {
		fprintf(stderr, "Request: Client requested a chunk that we don't have\n");
        delete_client(in->tdata, in->client);
        return 1;
	}
	
    char msghdr[MSG_PIECE_HDR_LENGTH];
	msghdr[0] = MSG_PIECE;
    *((uint32_t *) (msghdr + 1)) = htonl(piece);
    *((uint32_t *) (msghdr + 5)) = htonl(begin);
	
    send_message(in->tdata, in->client, msghdr, MSG_PIECE_HDR_LENGTH, 
                 in->tdata->file + piece * in->tdata->piece_size + begin, length);
    
	return 0;
}

int Unchoke (Unchoke_in *in, Unchoke_out *out) {
	out->tdata = in->tdata;
	out->client = in->client;
	
    in->client->choked = false;
    
	return 0;
}

int SendChokeUnchoke (SendChokeUnchoke_in *in) {
	std::vector<client_data_t *>::iterator i;
    
    static char chokemsg[] = {MSG_CHOKE};
    static char unchokemsg[] = {MSG_UNCHOKE};
	
	for (i = in->clist.choke.begin(); i != in->clist.choke.end(); i++)
        send_message(in->tdata, *i, chokemsg, MSG_CHOKE_LENGTH, NULL, 0);
	
	for (i = in->clist.unchoke.begin(); i != in->clist.unchoke.end(); i++)
        send_message(in->tdata, *i, unchokemsg, MSG_UNCHOKE_LENGTH, NULL, 0);
	
	return 0;
}

int HandleMessage (HandleMessage_in *in, HandleMessage_out *out) {
	return 0; 
}

int Uninterested (Uninterested_in *in, Uninterested_out *out) { 
	out->client = in->client;
    
    in->client->interested = false;
    return 0;    
}

int SendBitfield (SendBitfield_in *in) {
    if (!in->client)
        return 0;
    
    int length = MSG_BITFIELD_HDR_LENGTH + (in->tdata->pieces + 7) / 8;
    char *bfmsg = new char[length];
    bfmsg[0] = MSG_BITFIELD;

    memcpy(bfmsg + MSG_BITFIELD_HDR_LENGTH, in->tdata->bit_field, 
           length - MSG_BITFIELD_HDR_LENGTH);
    in->tdata->clients.push_back(in->client);
    
    send_message(in->tdata, in->client, bfmsg, length, NULL, 0);
    
    delete [] bfmsg;
	
	return 0;
}

int PickChoked (PickChoked_in *in, PickChoked_out *out) {
	out->tdata = in->tdata;
	

	// For now just be nice and unchoke *everyone*	
	std::list<client_data_t *>::iterator i;
	for (i = in->tdata->clients.begin(); i != in->tdata->clients.end(); i++) {
		if ((*i)->choking)
			out->clist.unchoke.push_back(*i);
        (*i)->choking = false;
	}
	
    return 0;
}

int ReadMessage (ReadMessage_in *in, ReadMessage_out *out) {
	out->tdata = in->tdata;
	out->client = in->client;
	out->type = -1;
	out->payload = NULL;
    
	char msgtype;
	
    // FIXME This should actually be a while loop, for the
    // odd case in which read reads less than 4 bytes...
    if (read(in->client->socket, &(out->length), sizeof(uint32_t)) != sizeof(uint32_t)) {
        delete_client(in->tdata, in->client);
        return 1;        
    }
    
    out->length = ntohl(out->length);
    
    // printf("Receiving message from %d, length %d\n", in->client->socket, out->length);
    
	if (out->length == 0) {
        in->client->listenable = true;
//        printf("Received keepalive from %d\n", in->client->socket);
		return 0;
	}
		
	out->length--;
	
    if (read(in->client->socket, &msgtype, 1) != 1) {
        delete_client(in->tdata, in->client);
        return 1;
    }
	out->type = msgtype;
	
	if (out->length == 0) {
        in->client->listenable = true;
//        printf("Sending keepalive on %d\n", in->client->socket);
        return 0;
	}
	
	out->payload = new char[out->length];
	int total_read = 0, bytes_read = 1;
	while (total_read < out->length && bytes_read > 0)
		total_read += bytes_read = read(in->client->socket, out->payload, 
			out->length - total_read);
	if (bytes_read < 0 || total_read != out->length) {
		fprintf(stderr, "ReadMessage: Error reading in message\n");
        delete_client(in->tdata, in->client);
		return 1;
	}
    
    /* if (out->type != 6) {
        printf("Receiving from %d, %s message (%d bytes): ", 
           in->client->socket,
           out->type < 9 ? MESSAGE_TYPES[out->type] : "unknown",
           out->length + 1);
        if (out->length < 20)
            print_hex((unsigned char *) out->payload, out->length);
        printf("\n");
    } */
	
	in->client->listenable = true;
	return 0;
}

int TrackerTimer (TrackerTimer_out *out) {
    sleep(tdata.interval);
    out->tdata = &tdata;
    return 0;
}

int ChokeTimer (ChokeTimer_out *out) {
    sleep(CHOKE_INTERVAL);
    out->tdata = &tdata;
    return 0;
}

int KeepAliveTimer (KeepAliveTimer_out *out) {
    sleep(KEEPALIVE_INTERVAL);
    out->tdata = &tdata;
    return 0;
}

int Connect (Connect_out *out) {
    out->tdata = &tdata;

    if ((out->socket = accept (tdata.listen_sock, 0, 0 )) < 0) {
	perror ("accept");
	return -1;
    }

    return 0;
}

int GetClients (GetClients_out *out) {
    if (incomingmsgq.size() > 0) {
	out->fds = NULL;
	return 0;
    }

    out->fds = new fd_set;

    FD_ZERO(out->fds);

    client_data_t *curr_client;
    std::list<client_data_t *>::iterator i;
    int max_socket = 0;
    //int num
    for(i = tdata.clients.begin(); i != tdata.clients.end(); i++ ) 
        if ((*i)->listenable)
	{
	    curr_client = *i;

	    FD_SET(curr_client->socket, out->fds);
	    if (curr_client->socket > out->maxfd)
		out->maxfd = curr_client->socket;
	}

    return 0;
}

int CheckSockets (CheckSockets_in *in, CheckSockets_out *out) {
    client_data_t *curr_client;
    std::list<client_data_t *>::iterator i;

    if (in->fds) {
	for(i = tdata.clients.begin(); i != tdata.clients.end(); i++) 
	{
	    curr_client = *i;

	    if (FD_ISSET(curr_client->socket, in->fds))
	    {
		curr_client->listenable = false;
		incomingmsgq.push(curr_client);
	    }
	}

	delete in->fds;
    }

    if (incomingmsgq.size() == 0)
	return -1;

    out->tdata = &tdata;
    out->client = incomingmsgq.front();
    incomingmsgq.pop();
    return 0;
}

int SelectSockets (SelectSockets_in *in, SelectSockets_out *out) {
    out->fds = in->fds;
    if (!in->fds)
	return 0;

    struct timeval timeout;
    timeout.tv_sec = 0;
    timeout.tv_usec = 0;

    select(in->maxfd + 1, in->fds, NULL, NULL, &timeout);

    return 0;
}

bool TestChoke(int type) {
    return type == MSG_CHOKE;
}

bool TestUnchoke(int type) {
    return type == MSG_UNCHOKE;
}

bool TestInterested(int type) {
    return type == MSG_INTERESTED;
}

bool TestUninterested(int type) {
    return type == MSG_NOTINTERESTED;
}

bool TestRequest(int type) {
    return type == MSG_REQUEST;
}

bool TestCancel(int type) {
    return type == MSG_CANCEL;
}

bool TestPiece(int type) {
    return type == MSG_PIECE;
}

bool TestBitfield(int type) {
    return type == MSG_BITFIELD;
}

bool TestHave(int type) {
    return type == MSG_HAVE;
}

bool TestPieceComplete(int piece) {
    return piece != -1;
}
