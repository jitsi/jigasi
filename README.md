Jigasi
======

Jitsi Gateway to SIP : a server-side application that links allows regular SIP clients to join Jitsi Meet conferences hosted by Jitsi Videobridge.

Install and run
============

It is possible to install Jigasi along with Jitsi Meet using our [quick install instructions] or do this from sources using the instructions below.

[quick install instructions]: https://github.com/turint/jitsi-meet/blob/master/doc/quick-install.md

1. Checkout latest source:
 
 ```
 git clone https://github.com/jitsi/jigasi.git
 ```
2. Build:

 ```
 cd jigasi
 ant make
 ```
 
3. Configure external component in your XMPP server. If your server is Prosody: edit /etc/prosody/prosody.cfg.lua and append following lines to your config (assuming that subdomain is 'callcontrol' and domain 'meet.jit.si'):

 ```
 Component "callcontrol.meet.jit.si"
     component_secret = "topsecret"
 ```
4. Setup SIP account

 Go to jigasi/jigasi-home and edit sip-communicator.properties file. Replace ```<<JIGASI_SIPUSER>>``` tag with SIP username for example: "user1232@sipserver.net". Then put Base64 encoded password in place of ```<<JIGASI_SIPPWD>>```.

5. Start Jigasi
 
 ```
 ./jigasi.sh --domain=meet.jit.si --subdomain=callcontrol --secret=topsecret
 ```
After Jigasi is started it will register as XMPP component under the 'callcontrol' subdomain. In Jitsi Meet application -> config.js -> hosts.call_control must be set to 'callcontrol.meet.jit.si'. This will enable SIP calls in Jitsi Meet.

Supported arguments:
 * --domain: specifies the XMPP domain to use.
 * --host: the IP address or the name of the XMPP host to connect to (localhost by default).
 * --port: the port of the XMPP host to connect on (5347 by default).
 * --subdomain: subdomain name for SIP gateway component.
 * --secret: the secret key for the sub-domain of the Jabber component implemented by this application with which it is to authenticate to the XMPP server to connect to.
 * --min-port: the minimum port number that we'd like our RTP managers to bind upon.
 * --max-port: the maximum port number that we'd like our RTP managers to bind upon.

How it works
============

Jigasi registers as a SIP client and can be called or be used by Jitsi Meet to make outgoing calls. Jigasi is NOT a SIP server. It is just a connector that allows SIP servers and B2BUAs to connect to Jitsi Meet. It handles the XMPP signalling, ICE, DTLS/SRTP termination and multiple-SSRC handling for them.

Outgoing calls
==============

To call someone from Jitsi Meet application, Jigasi must be configured and started like described in the 'Install and run' section. This will cause the telephone icon to appear in the toolbar which will popup a call dialog on click.

Incoming calls
==============

Jigasi will register on your SIP server with some identity and it will accept calls. When Jigasi is called, it expects to find a 'Jitsi-Conference-Room' header in the invite with the name of the Jitsi Meet conference the call is to be patched through to. If no header is present it will join the room specified under 'org.jitsi.jigasi.DEFAULT_JVB_ROOM_NAME' config property. In order to change it, edit 'jigasi-home/sipcommunciator.properties' file.

Example:

Received SIP INVITE with room header 'Jitsi-Conference-Room': 'room1234' will cause Jigasi to join the conference 'https://meet.jit.si/room1234' (assuming that our domain is 'meet.jit.si').
