// tunnel.cpp : Defines the entry point for the console application.
// http://www.winsocketdotnetworkprogramming.com/winsock2programming/winsock2advancedmulticast9a.html

#include "stdafx.h"

#include <list>
#include "zmq.hpp"
#include <cstring>
#include <pthread.h>
#include <semaphore.h>

#include <winsock2.h>
#include <ws2tcpip.h>
#include <mswsock.h>
#include "resolve.h"

#define u_int32 UINT32  // Unix uses u_int32


/*********************
* ZeroMQ Operations *
*********************/

void s_free(void *data, void *hint) {
	free(data);
}

void s_recv(zmq::socket_t &socket, zmq::message_t &msg) {
	if (!socket.recv(&msg)) {
		fprintf(stderr, "ERROR: Receiving data\n");
	}
}

void s_recv_empty(zmq::socket_t &socket) {
	zmq::message_t msg(1);
	s_recv(socket, msg);
}

int s_send(zmq::socket_t &socket, std::string buf) {
	zmq::message_t msg(buf.c_str(), buf.length());
	socket.send(msg);
	return 0;
}

void s_send_empty(zmq::socket_t &socket) {
	char empty[1] = "";
	s_send(socket, empty);
}

/*********************
* Socket Operations *
*********************/

struct sockaddr_in setup_addr(const char *ip_addr, int port) {
	struct sockaddr_in addr;

	/* set up destination address */
	memset(&addr, 0, sizeof(addr));
	addr.sin_family = AF_INET;
	if (ip_addr != NULL) {
		//struct sockaddr_in iaddr;

		//iaddr.s_addr = inet_pton("226.0.0.1");
		inet_pton(AF_INET, ip_addr, &(addr.sin_addr.s_addr));
		//addr.sin_addr.s_addr = inet_pton(ip_addr);
	}
	else {
		addr.sin_addr.s_addr = htons(INADDR_ANY);
	}
	addr.sin_port = htons(port);

	return addr;
}

SOCKET listen_mc(struct sockaddr_in addr, std::string mcast_addr) {
	struct ip_mreq mreq;
	SOCKET fd;
	u_int yes = 1;

	if ((fd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
		perror("socket");
		exit(1);
	}

	if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (char *) &yes, sizeof(yes)) < 0) {
		perror("Reusing ADDR failed");
		exit(1);
	}

	/* bind to receive address */
	if (bind(fd, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
		perror("bind");
		exit(1);
	}

	inet_pton(AF_INET, mcast_addr.c_str(), &(mreq.imr_multiaddr.s_addr));
	//mreq.imr_multiaddr.s_addr = inet_addr(mcast_addr.c_str());
	mreq.imr_interface.s_addr = htons(INADDR_ANY);

	/* use setsockopt() to request that the kernel join a multicast group */
	if (setsockopt(fd, IPPROTO_IP, IP_ADD_MEMBERSHIP, (char *) &mreq, sizeof(mreq)) < 0) {
		//perror("setsockopt");
		//exit(1);
	}

	return fd;
}

/***********
* GLOBALS *
***********/

const char *server_addr = NULL;
const char *multicast_group = NULL;
bool verbose = false;

SOCKET client_mc_socket() {
	SOCKET fd;
	int ttl = 1;
	int allow_loop = 0;
	//struct in_addr iaddr;
	struct sockaddr_in iaddr;

	//iaddr.s_addr = inet_pton("226.0.0.1");
	inet_pton(AF_INET, multicast_group, &(iaddr.sin_addr));

	if ((fd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
		//perror("socket");
		//exit(1);
	}

	if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF, (char *) &iaddr, sizeof(struct sockaddr_in))) {
		//perror("setsockopt");
		//exit(1);
	}

	if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_TTL, (char *) &ttl, sizeof(unsigned char))) {
		//perror("setsockopt");
		//exit(1);
	}

	if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_LOOP, (char *) &allow_loop, sizeof(unsigned char))) {
		//perror("setsockopt");
		//exit(1);
	}

	//perror("setsockopt");

	return fd;
}

/*********************
* Message Structure *
*********************/

#define BUFSIZE 65535
#define MAX_BUFSIZE sizeof(uint32_t) + BUFSIZE
#define HDR_LEN(buflen) (buflen + sizeof(uint16_t) + sizeof(uint32_t))

#pragma pack(push, 2)
typedef struct mcast_tunnel_msg {
	uint16_t port;
	uint32_t buflen;
	char buf[BUFSIZE];
} mcast_tunnel_msg_t;
#pragma pack(pop)

char *gen_zmq_addr(const char *ipaddr, int port) {
	char *zmq_addr = (char *)malloc(strlen(ipaddr) + 16);
	sprintf_s(zmq_addr, 255, "tcp://%s:%d", ipaddr, port);
	return zmq_addr;
}

void *start_sub(void *args) {
	zmq::context_t context(1);
	zmq::socket_t subscriber(context, ZMQ_SUB);
	//zmq::socket_t syncclient(context, ZMQ_REQ);

	struct sockaddr_in mcast_addr;
	socklen_t socklen = sizeof(mcast_addr);
	SOCKET mcast_fd;
	char *client_addr;

	client_addr = gen_zmq_addr((char *)args, 5556);
	subscriber.connect(client_addr);
	subscriber.setsockopt(ZMQ_SUBSCRIBE, "", 0);
	//free(client_addr);

	mcast_fd = client_mc_socket();

	////  Handshake socket
	//client_addr = gen_zmq_addr((char *)args, 5562);
	//syncclient.connect(client_addr);
	//free(client_addr);

	//free(args);

	// handshake
	//s_send_empty(syncclient);
	//s_recv_empty(syncclient);

	mcast_addr = setup_addr(multicast_group, 3000);

	while (1) {
		zmq::message_t msg(MAX_BUFSIZE);
		s_recv(subscriber, msg);

		mcast_tunnel_msg_t *tunnel_msg = (mcast_tunnel_msg_t *)msg.data();
		tunnel_msg->port = ntohs(tunnel_msg->port);
		tunnel_msg->buflen = ntohl(tunnel_msg->buflen);
		tunnel_msg->buf[tunnel_msg->buflen] = '\0';

		if (verbose) {
			printf("Received msg of size %d over tunnel\n", tunnel_msg->buflen);
		}

		//send the message
		if (sendto(mcast_fd, tunnel_msg->buf, tunnel_msg->buflen, 0, (struct sockaddr *)&mcast_addr, socklen) < 0) {
			//perror("sendto()");
		}
	}
}

void *start_pub(void *args) {
	zmq::context_t context(1);
	zmq::socket_t publisher(context, ZMQ_PUB);
//	zmq::socket_t syncservice(context, ZMQ_REP);

	std::list<uint16_t> *mcast_ports = (std::list<uint16_t> *)args;
	size_t num_ports = mcast_ports->size();
	zmq_pollitem_t* items = new zmq_pollitem_t[num_ports];

	mcast_tunnel_msg_t tunnel_msg;
	struct sockaddr_in* mcast_addrs = new sockaddr_in[num_ports];
	struct sockaddr_in addr;
	struct in_addr server_inaddr;
	socklen_t socklen = sizeof(addr);

	char *pub_addr;
	//char *rep_addr;
	int ret;
	int i;

	////char szHostName[255];
	////gethostname(szHostName, 255);
	////struct hostent *host_entry;
	////host_entry = getaddrinfo();// (szHostName);
	//struct addrinfo hints, *infoptr = NULL;
	//hints.ai_family = AF_UNSPEC;
	//hints.ai_socktype = SOCK_STREAM;
	//hints.ai_protocol = IPPROTO_TCP;

	//DWORD dwRetval = getaddrinfo("localhost", NULL, &hints, &infoptr);
	//if (dwRetval != 0) {
	//	printf("getaddrinfo failed with error: %d\n", dwRetval);
	//	exit(1);
	//}
	//sockaddr_in *sockaddr_ipv4 = (struct sockaddr_in *) infoptr->ai_addr;
	//char* ans = new char[80];
	//inet_ntop(AF_INET, (void *)infoptr, ans, 80);

	//printf("IPv4 address %s\n", ans);
	//delete[] ans;

	//char * szLocalIP;
	//szLocalIP = inet_ntoa(*(struct in_addr *)*host_entry->h_addr_list);

	//in_addr loc = doit();
	// publish socket
	pub_addr = gen_zmq_addr("0.0.0.0", 5556);
	publisher.bind(pub_addr);

	//// Socket to receive handshakes
	//rep_addr = gen_zmq_addr(server_addr, 5562);
	//syncservice.bind("tcp://*:5562");

	free(pub_addr);
	//free(rep_addr);

	inet_pton(AF_INET, server_addr, &(server_inaddr.s_addr));
	//server_inaddr.s_addr = inet_addr(server_addr);

	/*
	* Setup poller. First entry is the respond socket, other entries are for
	* multicast sockets. The respond socket sends an empty string back to a
	* subscriber (handshake). This tells the subscriber the publisher is ready
	* to send data.  When we receive traffic on a multicast socket, we will echo
	* it onto the publish socket.
	*/
	//memset(items, 0, sizeof(zmq_pollitem_t) * (num_ports + 1));
	//memset(mcast_addrs, 0, sizeof(struct sockaddr_in) * num_ports);

	//items[0].socket = syncservice;
	//items[0].events = ZMQ_POLLIN;

	i = 0;
	for (uint16_t mcast_port : *mcast_ports) {
		if (verbose) {
			printf("Listening to ");
			printf(multicast_group);
			printf("\n");
			//cout << "Listening to " << multicast_group << ":" << mcast_port << endl;
		}
		mcast_addrs[i] = setup_addr(NULL, mcast_port);
		items[i].fd = listen_mc(mcast_addrs[i], multicast_group);
		items[i].events = ZMQ_POLLIN;
		i++;
	}

	while (1) {
		//ret = zmq::poll(items, num_ports + 1, -1);
		//assert(ret >= 0);

		//// Syncservice
		//if (items[0].revents & ZMQ_POLLIN) {
		//	//cout << "Received incoming connection" << endl;
		//	// handshake with client
		//	s_recv_empty(syncservice);
		//	s_send_empty(syncservice);
		//}

		// Multicast Sockets
		//printf("%d", items[0].fd);
		if ((ret = recvfrom(items[0].fd, tunnel_msg.buf, BUFSIZE, 0, (struct sockaddr *)&addr, &socklen)) < 0) {
			//perror("recvfrom");
			//exit(1);
		}

		tunnel_msg.port = mcast_addrs[0].sin_port;
		tunnel_msg.buflen = htonl(ret);
		//printf("Length: %d\n", tunnel_msg.buflen);
		tunnel_msg.port = mcast_addrs[i].sin_port;
		tunnel_msg.buflen = htonl(ret);

		zmq::message_t msg(&tunnel_msg, HDR_LEN(ret), NULL, NULL);
		publisher.send(msg);

		if (verbose) {
			printf("Sent msg of size %d to pubsocket\n", ret);
		}

		//for (i = 0; i < num_ports; i++) {
		//	if (items[i + 1].revents & ZMQ_POLLIN) {
		//		if ((ret = recvfrom(items[i + 1].fd, tunnel_msg.buf, BUFSIZE, 0, (struct sockaddr *)&addr, &socklen)) < 0) {
		//			perror("recvfrom");
		//			exit(1);
		//		}

		//		///* Do not rebroadcast packets from our subscribers */
		//		//if (server_inaddr.s_addr != addr.sin_addr.s_addr) {
		//		//	tunnel_msg.port = mcast_addrs[i].sin_port;
		//		//	tunnel_msg.buflen = htonl(ret);

		//		//	zmq::message_t msg(&tunnel_msg, HDR_LEN(ret), NULL, NULL);
		//		//	publisher.send(msg);

		//		//	if (verbose) {
		//		//		//cout << "Sent msg of size " << ret << " from " << inet_ntoa(addr.sin_addr) << " to pub socket" << endl;
		//		//	}
		//		//}
		//		//else if (verbose) {
		//		//	//cout << "Dropping packet from ourselves of size " << ret << endl;
		//		//}
		//	}
		//}
	}
}

int main(int argc, char** argv)
{
	WSADATA             wsd;
	//SOCKET              s;
	struct addrinfo    *resmulti = NULL, *resbind = NULL, *resif = NULL;
	char               *buf = NULL;
	//int                 rc, i = 0;

	verbose = true;

	// Parse the command line
	// Load Winsock
	if (WSAStartup(MAKEWORD(1, 1), &wsd) != 0)
	{
		printf("WSAStartup failed\n");
		return -1;
	}

	std::list<uint16_t> *mcast_ports = new std::list<uint16_t>;
	mcast_ports->push_back(3000);
	multicast_group = "226.0.1.1";
	//start_pub(mcast_ports);
	start_sub((void *) "192.168.0.62");

	WSACleanup();
	return 0;
}
