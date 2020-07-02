#include "common.h"

void usage() {
    puts ("Usage: noticeserver [-a <address>] [-p <port number>] [-f <-a address> <-p port>] [-d]\n");
    puts ("-a          - ip address to bind to (admin client address in -f mode)\n");
    puts ("-p          - port number (admin client port in -f mode)\n");
    puts ("-f          - register 'admin' address and port\n");
    puts ("-d          - debug mode, message output to syslog\n");
    exit(0);
}

int main(int argc, char *argv[]) {
    extern int          optind,opterr,optopt;
    extern char*        optarg;
    int                 ch;
    int                 rc;             /* system calls return value storage */
    int                 sfd;            /* socket descriptor */
    char                buf[BUFLEN+1];  /* buffer for incoming data */
    struct sockaddr_in  sa;             /* Internet address struct */
    struct sockaddr_in  csa;            /* Client's Internet address struct */
    unsigned int        sa_len;
    char                ip[50];
    char                tport[7];
    unsigned short      port=0;
    struct              hostent* h;
    int                 option_value;   /* needed for setsockopt */
    DEPOT*              ipDB;
    DEPOT*              portDB;
    int                 success=1;        /* indicates a successful registration - send confirmation */
    int                 debug=0;
    int                 fflag=0, aflag=0, pflag=0;

    strcpy(ip,"");

    /* parse option parameters */
    while ((ch = getopt(argc, argv, "a:p:fd")) != EOF) {
        switch ((char)ch) {
        case 'a':       /* get address */
            aflag = 1;
            strcpy(ip,optarg);
            break;

        case 'p':       /* get port */
            pflag = 1;
            port = atoi(optarg);
            break;

        case 'f':       /* admin registration */
            fflag = 1;
            break;

        case 'd':       /* turn on debug */
            debug = 1;
            break;

        default:
            usage();
            break;
        }
    }

    if (fflag) {
        if (aflag & pflag) {
            if(!(ipDB = dpopen(IP_DBNAME, DP_OWRITER | DP_OCREAT, -1))) {
                openlog("noticeserver", 0, LOG_DAEMON);
                syslog(LOG_ERR, "dpopen ipDB (admin): %s", dperrmsg(dpecode));
                closelog();
                exit(1);
            }

            /* store the record */
            if(!dpput(ipDB, "admin", -1, ip, -1, DP_DOVER)) {
                openlog("noticeserver", 0, LOG_DAEMON);
                syslog(LOG_ERR, "dpput ipDB (admin): %s", dperrmsg(dpecode));
                closelog();
                exit(1);
            }

            /* close the database*/
            if(!dpclose(ipDB)){
                openlog("noticeserver", 0, LOG_DAEMON);
                syslog(LOG_ERR, "dpclose ipDB (admin): %s", dperrmsg(dpecode));
                closelog();
                exit(1);
            }

            // store client port number
            /* open the database */
            if(!(portDB = dpopen(PORT_DBNAME, DP_OWRITER | DP_OCREAT, -1))) {
                openlog("noticeserver", 0, LOG_DAEMON);
                syslog(LOG_ERR, "dpopen portDB (admin): %s", dperrmsg(dpecode));
                closelog();
                exit(1);
            }

            /* store the record */
            sprintf(tport, "%i", port);
            if(!dpput(portDB, "admin", -1, tport, -1, DP_DOVER)) {
                openlog("noticeserver", 0, LOG_DAEMON);
                syslog(LOG_ERR, "dpput portDB (admin): %s", dperrmsg(dpecode));
                closelog();
                exit(1);
            }

            /* close the database*/
            if(!dpclose(portDB)) {
                openlog("noticeserver", 0, LOG_DAEMON);
                syslog(LOG_ERR, "dpclose portDB (admin): %s", dperrmsg(dpecode));
                closelog();
                exit(1);
            }
            exit(0);
        } else {
            usage();
        }
    } else {
        /* initiate Internet address structure */
        sa_len = sizeof(sa);
        if (port == 0) port=S_PORT;
        memset(&sa, 0, sa_len);
        sa.sin_family = AF_INET;
        sa.sin_port = htons(port);
        if (!strcmp(ip,"")) {
            /* Local machine on IP. */
            sa.sin_addr.s_addr = htonl(INADDR_ANY);
        } else {
            /* Get IP address (no check if input is IP address or DNS name). */
            h = gethostbyname(ip);
            if (h == NULL) {
                openlog("noticeserver", 0, LOG_DAEMON);
                syslog(LOG_ERR, "Unknown host '%s'", ip);
                closelog();
                exit(1);
            }
            memcpy(&sa.sin_addr.s_addr, h->h_addr_list[0], h->h_length);
        }

        /* allocate a socket */
        if ((sfd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
            openlog("noticeserver", 0, LOG_DAEMON);
            syslog(LOG_ERR, "socket: failed!");
            closelog();
            exit(1);
        }

        /* make listening socket's port reusable - must be before bind */
        option_value = 1;
        if (setsockopt(sfd, SOL_SOCKET, SO_REUSEADDR, (char *)&option_value, 
            sizeof(option_value)) < 0) {
                openlog("noticeserver", 0, LOG_DAEMON);
                syslog(LOG_ERR, "setsockopt failed");
                closelog();
                exit(1);
        }

        /* bind the socket to the address */
        if (bind(sfd, (struct sockaddr *)&sa, sa_len)) {
            openlog("noticeserver", 0, LOG_DAEMON);
            syslog(LOG_ERR, "bind: failed!");
            closelog();
            exit(1);
        }

        while(1) {
            rc = recvfrom(sfd, buf, BUFLEN, 0, (struct sockaddr *)&csa, &sa_len); 
            if (rc < 0) {
                openlog("noticeserver", 0, LOG_DAEMON);
                syslog(LOG_ERR, "recvfrom error");
                closelog();
            } else {
                buf[rc] = '\0';
                if (debug) {
                    openlog("noticeserver", 0, LOG_DAEMON);
                    syslog(LOG_INFO, "Registration: %s   IP: %s   Port: %u", buf, inet_ntoa(csa.sin_addr), ntohs(csa.sin_port));
                    closelog();
                }
                sprintf(tport, "%u", ntohs(csa.sin_port));

                // store client IP address
                /* open the database */
                if(!(ipDB = dpopen(IP_DBNAME, DP_OWRITER | DP_OCREAT, -1))) {
                    openlog("noticeserver", 0, LOG_DAEMON);
                    syslog(LOG_ERR, "dpopen ipDB: %s", dperrmsg(dpecode));
                    closelog();
                    exit(1);
                }

                /* store the record */
                if(!dpput(ipDB, buf, -1, inet_ntoa(csa.sin_addr), -1, DP_DOVER)) {
                    openlog("noticeserver", 0, LOG_DAEMON);
                    syslog(LOG_ERR, "dpput ipDB: %s", dperrmsg(dpecode));
                    closelog();
                    success = 0;
                }

                /* close the database*/
                if(!dpclose(ipDB)){
                    openlog("noticeserver", 0, LOG_DAEMON);
                    syslog(LOG_ERR, "dpclose ipDB: %s", dperrmsg(dpecode));
                    closelog();
                    exit(1);
                }

                // store client port number
                /* open the database */
                if(!(portDB = dpopen(PORT_DBNAME, DP_OWRITER | DP_OCREAT, -1))) {
                    openlog("noticeserver", 0, LOG_DAEMON);
                    syslog(LOG_ERR, "dpopen portDB: %s", dperrmsg(dpecode));
                    closelog();
                    exit(1);
                }

                /* store the record */
                if(!dpput(portDB, buf, -1, tport, -1, DP_DOVER)) {
                    openlog("noticeserver", 0, LOG_DAEMON);
                    syslog(LOG_ERR, "dpput portDB: %s", dperrmsg(dpecode));
                    closelog();
                    success = 0;
                }

                /* close the database*/
                if(!dpclose(portDB)) {
                    openlog("noticeserver", 0, LOG_DAEMON);
                    syslog(LOG_ERR, "dpclose portDB: %s", dperrmsg(dpecode));
                    closelog();
                    exit(1);
                }

                if (success) {
                    if (debug) {
                        openlog("noticeserver", 0, LOG_DAEMON);
                        syslog(LOG_INFO, "Sending confirmation: %s", buf);
                        closelog();
                    }
                    rc = sendto(sfd, buf, strlen(buf), 0, (struct sockaddr *)&csa, sa_len);
                    if (rc < 0) {
                        openlog("noticeserver", 0, LOG_DAEMON);
                        syslog(LOG_ERR, "sendto error - confirmation %s", buf);
                        closelog();
                    }
                }
            }
        }
    }
    exit(0);
}
