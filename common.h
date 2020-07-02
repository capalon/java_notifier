#include <sys/types.h>          /* standard system types        */
#include <netinet/in.h>         /* Internet address structures  */
#include <sys/socket.h>         /* socket interface functions   */
#include <netdb.h>              /* host to IP resolution        */
#include <sys/time.h>           /* for timeout values           */
#include <unistd.h>             /* for table size calculations  */
#include <string.h>
#include <arpa/inet.h>
#include <stdio.h>              /* Basic I/O routines           */
#include <qdbm/depot.h>         /* QDBM routines           */
#include <stdlib.h>
#include <syslog.h>

#define S_PORT            5070  /* port of our server */
#define BUFLEN          256     /* buffer length           */
#define IP_DBNAME		"/var/spool/notifier/notifyip.db"
#define PORT_DBNAME		"/var/spool/notifier/notifyport.db"
#define DASHBOARD_CONF		"/etc/dashboard.conf"
