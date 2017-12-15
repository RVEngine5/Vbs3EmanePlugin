// tunnel.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"

#include "zmq.hpp"
#include <cstring>
#include <pthread.h>
#include <semaphore.h>

#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>

#include "resolve.h"

using namespace std;

// Function: JoinMulticastGroup
// Description:
//    This function joins the multicast socket on the specified multicast
//    group. The structures for IPv4 and IPv6 multicast joins are slightly
//    different which requires different handlers. For IPv6 the scope-ID
//    (interface index) is specified for the local interface whereas for IPv4
//    the actual IPv4 address of the interface is given.
int JoinMulticastGroup(SOCKET s, struct addrinfo *group, struct addrinfo *iface)
{
	struct ip_mreq   mreqv4;
	struct ipv6_mreq mreqv6;
	char *optval = NULL;
	int    optlevel, option, optlen, rc;

	rc = NO_ERROR;
	if (group->ai_family == AF_INET)
	{
		// Setup the v4 option values and ip_mreq structure
		optlevel = IPPROTO_IP;
		option = IP_ADD_MEMBERSHIP;
		optval = (char *)& mreqv4;
		optlen = sizeof(mreqv4);

		mreqv4.imr_multiaddr.s_addr = ((SOCKADDR_IN *)group->ai_addr)->sin_addr.s_addr;
		mreqv4.imr_interface.s_addr = ((SOCKADDR_IN *)iface->ai_addr)->sin_addr.s_addr;
	}
	else if (group->ai_family == AF_INET6)
	{
		// Setup the v6 option values and ipv6_mreq structure
		optlevel = IPPROTO_IPV6;
		option = IPV6_ADD_MEMBERSHIP;
		optval = (char *)&mreqv6;
		optlen = sizeof(mreqv6);

		mreqv6.ipv6mr_multiaddr = ((SOCKADDR_IN6 *)group->ai_addr)->sin6_addr;
		mreqv6.ipv6mr_interface = ((SOCKADDR_IN6 *)iface->ai_addr)->sin6_scope_id;
	}
	else
	{
		fprintf(stderr, "Attempting to join multicast group for invalid address family!\n");
		rc = SOCKET_ERROR;
	}
	if (rc != SOCKET_ERROR)
	{
		// Join the group
		rc = setsockopt(s, optlevel, option, optval, optlen);
		if (rc == SOCKET_ERROR)
		{
			printf("JoinMulticastGroup: setsockopt failed with error code %d\n", WSAGetLastError());
		}
		else
		{
			printf("Joined group: ");
			PrintAddress(group->ai_addr, group->ai_addrlen);
			printf("\n");
		}
	}
	return rc;
}

// Function: SetSendInterface
// Description:
//    This routine sets the send (outgoing) interface of the socket.
//    Again, for v4 the IP address is used to specify the interface while
//    for v6 its the scope-ID.
int SetSendInterface(SOCKET s, struct addrinfo *iface)
{
	char *optval = NULL;
	int   optlevel, option, optlen, rc;

	rc = NO_ERROR;

	if (iface->ai_family == AF_INET)
	{
		// Setup the v4 option values
		optlevel = IPPROTO_IP;
		option = IP_MULTICAST_IF;
		optval = (char *) &((SOCKADDR_IN *)iface->ai_addr)->sin_addr.s_addr;
		optlen = sizeof(((SOCKADDR_IN *)iface->ai_addr)->sin_addr.s_addr);
	}
	else if (iface->ai_family == AF_INET6)
	{
		// Setup the v6 option values
		optlevel = IPPROTO_IPV6;
		option = IPV6_MULTICAST_IF;
		optval = (char *) &((SOCKADDR_IN6 *)iface->ai_addr)->sin6_scope_id;
		optlen = sizeof(((SOCKADDR_IN6 *)iface->ai_addr)->sin6_scope_id);
	}
	else
	{
		fprintf(stderr, "Attempting to set sent interface for invalid address family!\n");
		rc = SOCKET_ERROR;
	}

	// Set send IF
	if (rc != SOCKET_ERROR)
	{
		// Set the send interface
		rc = setsockopt(s, optlevel, option, optval, optlen);
		if (rc == SOCKET_ERROR)
		{
			printf("setsockopt() failed with error code %d\n", WSAGetLastError());
		}
		else
		{
			printf("Set sending interface to: ");
			PrintAddress(iface->ai_addr, iface->ai_addrlen);
			printf("\n");
		}
	}
	return rc;
}

// Function: SetMulticastTtl
// Description: This routine sets the multicast TTL value for the socket.
int SetMulticastTtl(SOCKET s, int af, int ttl)
{
	char *optval = NULL;
	int   optlevel, option, optlen, rc;

	rc = NO_ERROR;

	if (af == AF_INET)
	{
		// Set the options for V4
		optlevel = IPPROTO_IP;
		option = IP_MULTICAST_TTL;
		optval = (char *)&ttl;
		optlen = sizeof(ttl);
	}
	else if (af == AF_INET6)
	{
		// Set the options for V6
		optlevel = IPPROTO_IPV6;
		option = IPV6_MULTICAST_HOPS;
		optval = (char *)&ttl;
		optlen = sizeof(ttl);
	}
	else
	{
		fprintf(stderr, "Attempting to set TTL for invalid address family!\n");
		rc = SOCKET_ERROR;
	}
	if (rc != SOCKET_ERROR)
	{
		// Set the TTL value
		rc = setsockopt(s, optlevel, option, optval, optlen);
		if (rc == SOCKET_ERROR)
		{
			fprintf(stderr, "SetMulticastTtl: setsockopt() failed with error code %d\n", WSAGetLastError());
		}
		else
		{
			printf("Set multicast ttl to: %d\n", ttl);
		}
	}
	return rc;
}

// Function: SetMulticastLoopBack()
// Description:
//    This function enabled or disables multicast loopback. If loopback is enabled
//    (and the socket is a member of the destination multicast group) then the
//    data will be placed in the receive queue for the socket such that if a
//    receive is posted on the socket its own data will be read. For this sample
//    it doesn't really matter as if invoked as the sender, no data is read.
int SetMulticastLoopBack(SOCKET s, int af, int loopval)
{
	char *optval = NULL;
	int   optlevel, option, optlen, rc;

	rc = NO_ERROR;
	if (af == AF_INET)
	{
		// Set the v4 options
		optlevel = IPPROTO_IP;
		option = IP_MULTICAST_LOOP;
		optval = (char *)&loopval;
		optlen = sizeof(loopval);
	}
	else if (af == AF_INET6)
	{
		// Set the v6 options
		optlevel = IPPROTO_IPV6;
		option = IPV6_MULTICAST_LOOP;
		optval = (char *)&loopval;
		optlen = sizeof(loopval);
	}
	else
	{
		fprintf(stderr, "Attempting to set multicast loopback for invalid address family!\n");
		rc = SOCKET_ERROR;
	}
	if (rc != SOCKET_ERROR)
	{
		// Set the multipoint loopback
		rc = setsockopt(s, optlevel, option, optval, optlen);
		if (rc == SOCKET_ERROR)
		{
			fprintf(stderr, "SetMulticastLoopBack: setsockopt() failed with error code %d\n", WSAGetLastError());
		}
		else
		{
			printf("Setting multicast loopback to: %d\n", loopval);
		}
	}
	return rc;
}

/*********************
* ZeroMQ Operations *
*********************/

void s_free(void *data, void *hint) {
	free(data);
}

void s_recv(zmq::socket_t &socket, zmq::message_t &msg) {
	if (socket.recv(&msg) < 0) {
		fprintf(stderr, "ERROR: Receiving data\n");
	}
}

void s_recv_empty(zmq::socket_t &socket) {
	zmq::message_t msg(1);
	s_recv(socket, msg);
}

int s_send(zmq::socket_t &socket, string buf) {
	zmq::message_t msg(buf.c_str(), buf.length());
	socket.send(msg);
	return 0;
}

void s_send_empty(zmq::socket_t &socket) {
	char empty[1] = "";
	s_send(socket, empty);
}

int main(int argc, char** argv)
{
	WSADATA             wsd;
	SOCKET              s;
	struct addrinfo    *resmulti = NULL, *resbind = NULL, *resif = NULL;
	char               *buf = NULL;
	int                 rc, i = 0;

	// Parse the command line
	//ValidateArgs(argc, argv);
	// Load Winsock
	if (WSAStartup(MAKEWORD(1, 1), &wsd) != 0)
	{
		printf("WSAStartup failed\n");
		return -1;
	}
	else
	{
		printf("Hello World!\n");
	}

	//close resources...
	//freeaddrinfo(resmulti);
	//freeaddrinfo(resbind);
	//freeaddrinfo(resif);
	//closesocket(s);

	WSACleanup();
	return 0;
}
