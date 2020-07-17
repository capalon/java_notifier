# java_notifier
java notice app with C backend notice server and sender

This was created as an initial simple way to send desktop notifications from server processes when needed, and allow a response action if needed.

The desktop portion is written in Java.  I am not really a Java developer, but I managed to get it working.  It was originally created to be able to run as both an application and an applet.  I'm not sure the applet part is working any longer with the latest Java version as I have not tested that part recently.

The central notice server is written in C and is meant to run where it can be reached by all clients as it will serve multiple desktop clients.  The Java client makes an outgoing connection so it will work from behind a firewall without needing any firewall configuration.  The notice is sent using the notify C app which sends the message to the central server that routes it to the appropriate java client.

My current thought is to extend it as a CallerID popup notification app for use with an Asterisk server.

