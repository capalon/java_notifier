#include <signal.h>
#include "common.h"

int                 sfd;            /* socket descriptor */
unsigned int        sa_len;
struct sockaddr_in  sa;             /* Internet address struct */
struct sockaddr_in  csa;            /* Client's Internet address struct */
int                 fflag=0;
unsigned short      port=0;
struct hostent*     h;

void recvTimer() {
	sendto(sfd, "timeout", strlen("timeout"), 0, (struct sockaddr *)&csa, sa_len); 
	if (fflag) {    // send timeout to admin dashboard
		memcpy(&csa.sin_addr.s_addr, h->h_addr_list[0], h->h_length);
		csa.sin_port = port;
		sendto(sfd, "timeout", strlen("timeout"), 0, (struct sockaddr *)&csa, sa_len);
	}
	exit(112);
}

void usage() {
	puts ("Usage: notify -u <domain or userID> [-f] [-t <timeout>] [-m <message to send>] [-d]\n");
	puts ("-u          - domain/user to send message to\n");
	puts ("-m          - message to send to dashboard client\n");
	puts ("-f          - admin mode - send to default admin dashboard\n"); 
	puts ("              AND specified user with 10 minute timeout\n");
	puts ("-t          - specify notification timeout\n");
	puts ("-d          - debug mode, message output to syslog\n");
	exit(1);
}

int main(int argc, char *argv[]) {
	extern int          optind,opterr,optopt;
	extern char*        optarg;
	int                 ch, uflag=0, mflag=0, debug=0;
	int                 rc;             /* system calls return value storage */
	char                buf[BUFLEN+1];  /* buffer for incoming data */
	char*               ip;
	char                user[50], clientuser[50];
	char                *tport, *tclientport;
	unsigned short      clientport=0;
	struct hostent*     client;
	DEPOT*              regDB;
	int                 timeout=-1;     /* default timeout is 60 seconds unless we read differently from conf file */
	FILE*               conf_file;
	char                ticks[BUFLEN+1]; /* timeout value read from config file */
	struct sigaction    action;
	void (*func)() = recvTimer;
	char                response[20];

	/* parse option parameters */
	while ((ch = getopt(argc, argv, "dfm:t:u:")) != EOF) {
		switch ((char)ch) {
			case 'u':       /* get user/domain */
				strcpy(user,optarg);
				uflag = 1;
				break;

			case 'm':       /* get message */
				strcpy(buf,optarg);
				mflag = 1;
				break;

			case 'f':       /* admin mode - send to first address found */
				fflag = 1;
				break;

			case 't':   /* specify timeout value */
				timeout = atoi(optarg);
				break;

			case 'd':       /* turn on debug */
				debug = 1;
				break;

			default:
				usage();
				break;
		}
	}

	if (!uflag || !mflag) usage();

	if (fflag) {
		strcpy(clientuser, user);
		strcpy(user, "admin");
	}


	if (timeout == -1) {
		if ((conf_file = fopen (DASHBOARD_CONF, "r")) != NULL) {
			fgets(ticks, BUFLEN, conf_file);
			timeout = atoi(ticks);
		} else {
			timeout = 60;
		}
	}

	action.sa_handler = func;
	action.sa_flags = 0;
	sigemptyset(&(action.sa_mask));     /* ignore all known signals */
	sigaction(SIGALRM,&action,NULL);    /* ensures that SA_RESTART is NOT set */

	/* initiate Internet address structure */
	sa_len = sizeof(sa);
	if (port == 0) port=S_PORT;
	memset(&sa, 0, sa_len);
	memset(&csa, 0, sa_len);
	sa.sin_family = AF_INET;
	sa.sin_port = htons(port);
	csa.sin_family = AF_INET;

	/* Local machine on IP. */
	sa.sin_addr.s_addr = htonl(INADDR_ANY);

	// look up client IP address
	/* open database */
	if(!(regDB = dpopen(IP_DBNAME, DP_OREADER | DP_ONOLCK, -1))) {
		openlog("notify", 0, LOG_DAEMON);
		syslog(LOG_ERR, "dpopen ipDB: %s", dperrmsg(dpecode));
		closelog();
		exit(1);
	}

	/* find the user record */
	if(!(ip = dpget(regDB, user, -1, 0, -1, NULL))) {
		openlog("notify", 0, LOG_DAEMON);
		syslog(LOG_ERR, "dpget ipDB: %s", dperrmsg(dpecode));
		closelog();
		exit(1);
	}

	/* close the database */
	if(!dpclose(regDB)) {
		openlog("notify", 0, LOG_DAEMON);
		syslog(LOG_ERR, "dpclose ipDB: %s", dperrmsg(dpecode));
		closelog();
		exit(1);
	}

	// look up client port number
	/* open database */
	if(!(regDB = dpopen(PORT_DBNAME, DP_OREADER | DP_ONOLCK, -1))) {
		openlog("notify", 0, LOG_DAEMON);
		syslog(LOG_ERR, "dpopen portDB: %s", dperrmsg(dpecode));
		closelog();
		exit(1);
	}

	/* find the record */
	if(!(tport = dpget(regDB, user, -1, 0, -1, NULL))) {
		openlog("notify", 0, LOG_DAEMON);
		syslog(LOG_ERR, "dpget portDB: %s", dperrmsg(dpecode));
		closelog();
		if (!fflag) exit(1);
	} else {
		port = htons(atoi(tport));
	}

	/* find client user port record if in -f mode*/
	if (fflag) {
		if(!(tclientport = dpget(regDB, clientuser, -1, 0, -1, NULL))) {
			openlog("notify", 0, LOG_DAEMON);
			syslog(LOG_ERR, "dpget portDB 'clientuser': %s", dperrmsg(dpecode));
			closelog();
		} else {
			clientport = htons(atoi(tclientport));
		}
	}

	/* close the database */
	if(!dpclose(regDB)) {
		openlog("notify", 0, LOG_DAEMON);
		syslog(LOG_ERR, "dpclose portDB: %s", dperrmsg(dpecode));
		closelog();
		exit(1);
	}

	/* Get IP address (no check if input is IP address or DNS name). */
	h = gethostbyname(ip);
	if (h == NULL) {
		openlog("notify", 0, LOG_DAEMON);
		syslog(LOG_ERR, "Unknown host '%s'", ip);
		closelog();
		if (!fflag) exit(1);
	}
	memcpy(&csa.sin_addr.s_addr, h->h_addr_list[0], h->h_length);
	csa.sin_port = port;

	/* allocate a socket */
	if ((sfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
		openlog("notify", 0, LOG_DAEMON);
		syslog(LOG_ERR, "socket: failed!");
		closelog();
		exit(1);
	}


	rc = sendto(sfd, buf, strlen(buf), 0, (struct sockaddr *)&csa, sa_len); 
	if (rc < 0) {
		openlog("notify", 0, LOG_DAEMON);
		syslog(LOG_ERR, "sendto error");
		closelog();
		if (!fflag) exit(1);
	} else {
		if (debug) {
			openlog("notify", LOG_PID, LOG_DAEMON);
			syslog(LOG_INFO, "Sent to %s - %s", ip, buf);
			closelog();
		}
		if (fflag) {
			client = gethostbyname(clientuser);
			if (client == NULL) {
				openlog("notify", 0, LOG_DAEMON);
				syslog(LOG_ERR, "Unknown host '%s'", clientuser);
				closelog();
			} else {
				memcpy(&csa.sin_addr.s_addr, client->h_addr_list[0], client->h_length);
				csa.sin_port = clientport;
				rc = sendto(sfd, buf, strlen(buf), 0, (struct sockaddr *)&csa, sa_len);
				if (rc < 0) {
					openlog("notify", 0, LOG_DAEMON);
					syslog(LOG_ERR, "sendto client error");
					closelog();
				}
			}
		}
		strcpy(buf,"");
		alarm(timeout);
		rc = recvfrom(sfd, buf, BUFLEN, 0, (struct sockaddr *)&csa, &sa_len);
		alarm(0);       /* we got a message - disable alarm */
		if (rc < 0) {
			openlog("notify", 0, LOG_DAEMON);
			syslog(LOG_ERR, "recvfrom error");
			closelog();
			exit(1);
		} else {
			buf[rc] = '\0';
			if (debug) {
				openlog("notify", LOG_PID, LOG_DAEMON);
				syslog(LOG_INFO, "Received response: %s from   IP: %s   Port: %u", buf, inet_ntoa(csa.sin_addr), ntohs(csa.sin_port));
				closelog();
			}
			if (fflag) {
				if (!strcmp(buf,"0")) {
					strcpy(response,"accepted");
				} else {
					strcpy(response,"rejected");
				}
				if ((csa.sin_port == clientport) && (!strcmp(inet_ntoa(csa.sin_addr), clientuser))) {
					memcpy(&csa.sin_addr.s_addr, h->h_addr_list[0], h->h_length);
					csa.sin_port = port;
					sendto(sfd, response, strlen(response), 0, (struct sockaddr *)&csa, sa_len);
				} else {
					memcpy(&csa.sin_addr.s_addr, client->h_addr_list[0], client->h_length);
					csa.sin_port = clientport;
					sendto(sfd, response, strlen(response), 0, (struct sockaddr *)&csa, sa_len);
				}
			}
			close(sfd);
			return(atoi(buf));
		}
	}
	return(1);
}
