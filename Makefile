#
SHELL		= /bin/sh
CC		= gcc
ARCH           = $(shell uname -m)
#
CFLAGS       = $(RPM_OPT_FLAGS) -Wall -I/usr/local/include
LFLAGS         = -Xlinker -warn-common -L/usr/local/lib

LIBS		= -lqdbm
LINT		= lint -abchuvx
LLIBS		=	

# where things go
BINDIR		= /usr/local/bin

VERSION 	= 1
SUBVERSION 	= 0
PATCHLEVEL	= 0

# what are we making
SRC		= notify.c noticeserver.c
OBJ		= notify.o noticeserver.o 
BIN		= notify noticeserver

# what we are packaging
PACKAGE		= Makefile common.h\
		  notify notify.c noticeserver noticeserver.c  
TGZFILE		= notifier-$(VERSION).$(SUBVERSION).$(PATCHLEVEL).tar.gz

# rules

all:	$(BIN) java

install:  all
	install -s -m 755 $(BIN) $(BINDIR)			

notify:	notify.c common.h
	$(CC) $(CFLAGS) $(PFLAGS) $(LFLAGS) -o notify notify.c $(LIBS)

noticeserver: noticeserver.c common.h
	$(CC) $(CFLAGS) $(PFLAGS) $(LFLAGS) -o noticeserver noticeserver.c $(LIBS)

java: build.xml
	ant

debug:	$(SRC)
	$(CC) $(CFLAGS) -DDEBUG $(LFLAGS) -o notify notify.c $(LIBS)
	$(CC) $(CFLAGS) -DDEBUG $(LFLAGS) -o noticeserver noticeserver.c $(LIBS)

clean:
	rm -f *.o core *.out Makefile.old~
	rm -rf ./build
	rm ./notify
	rm ./noticeserver
	rm ./Dashboard.jar

uninstall:
	rm $(BINDIR)/notify
	rm $(BINDIR)/noticeserver

clobber: clean
	rm -f $(BIN)

package: all
	strip notify noticeserver
	tar cvf - $(PACKAGE) | gzip > ../$(TGZFILE)

