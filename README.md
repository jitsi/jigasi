Jigasi
======

Jitsi Gateway to SIP : a server-side application that links allows regular SIP clients to join Jitsi Meet conferences hosted by Jitsi Videobridge.

Install and run
============

It is possible to install Jigasi along with Jitsi Meet using our [quick install instructions] or do this from sources using the instructions below.

[quick install instructions]: https://github.com/jitsi/jitsi-meet/blob/master/doc/quick-install.md

1. Checkout latest source:
 
 ```
 git clone https://github.com/jitsi/jigasi.git
 ```
2. Build:

 ```
 cd jigasi
 mvn install -Dassembly.skipAssembly=false
 ```

3. Extract - choose either `jigasi-linux-x64-{version}.zip`, `jigasi-linux-x86-{version}.zip` or `jigasi-macosx-{version}.zip` based on the system.

 ```
 cd target/
 unzip jigasi-{os-version}-{version}.zip
 ```

4. Configure a muc component in your XMPP server that will be used for the brewery rooms. If your server is Prosody: edit /etc/prosody/prosody.cfg.lua or the appropriate file in /etc/prosody/conf.d and append following lines to your config (assuming that domain 'meet.example.com'):

 ```
Component "internal.auth.meet.example.com" "muc"
    storage = "memory"
    modules_enabled = {
      "ping";
    }
    admins = { "focus@auth.meet.example.com", "jigasi@auth.meet.example.com" }
    muc_room_locking = false
    muc_room_default_public_jids = true 
 ```

5. Setup SIP account

 Go to jigasi/jigasi-home and edit sip-communicator.properties file. Replace ```<<JIGASI_SIPUSER>>``` tag with SIP username for example: "user1232@sipserver.net". Then put Base64 encoded password in place of ```<<JIGASI_SIPPWD>>```.

6. Setup the xmpp account for jigasi control room (brewery).
    prosodyctl register jigasi auth.meet.example.com topsecret
    Replace ```<<JIGASI_XMPP_PASSWORD_BASE64>>``` tag with Base64 encoded password (topsecret) in the sip-communicator.properties file.

6. Start Jigasi
 
 ```
 cd jigasi/target/jigasi-{os-version}-{version}/
 ./jigasi.sh --domain=meet.example.com
 ```
After Jigasi is started it will register to the XMPP server and connect to the brewery room.

Supported arguments:
 * --domain: specifies the XMPP domain to use.
 * --host: the IP address or the name of the XMPP host to connect to (localhost by default).
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

Received SIP INVITE with room header 'Jitsi-Conference-Room': 'room1234' will cause Jigasi to join the conference 'https://meet.example.com/room1234' (assuming that our domain is 'meet.example.com').

Configuring SIP and Transcription
=======================================

It is possible to either enable or disable the functionality of SIP and 
transcription. By default, the properties 
`org.jitsi.jigasi.ENABLE_TRANSCRIPTION=false` 
and 
`org.jitsi.jigasi.ENABLE_SIP=true` 
in
`jigasi-home/sip-communicator.properties` 
enable SIP and disable transcription. To change this, simple set the desired
property to `true` or `false`.


Using Jigasi to transcribe a Jitsi Meet conference
==================================================

It is also possible to use Jigasi as a provider of nearly real-time transcription
as well as translation while a conference is ongoing as well as serving a complete transcription
after the conference is over. This can be done by using the SIP dial button and 
using the URI `jitsi_meet_transcribe`. 
Currently Jigasi can send speech-to-text results to
the chat of a Jitsi Meet room as either plain text or JSON. If it's send in JSON,
Jitsi Meet will provide subtitles in the left corner of the video, while plain text
will just be posted in the chat. Jigasi will also provide a link to where the final, 
complete transcript will be served when it enters the room.

Google configuration
====================

For jigasi to act as a transcriber, it sends the audio of all participants in the
room to an external speech-to-text service. To use [Google Cloud speech-to-text API](https://cloud.google.com/speech/)
it is required to install the [Google Cloud SDK](https://cloud.google.com/sdk/docs/)
on the machine running Jigasi. To install on a regular debian/ubuntu environment:

```
export CLOUD_SDK_REPO="cloud-sdk-$(lsb_release -c -s)"
echo "deb http://packages.cloud.google.com/apt $CLOUD_SDK_REPO main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
sudo apt-get update && sudo apt-get install google-cloud-sdk google-cloud-sdk-app-engine-java
gcloud init
gcloud auth application-default login
```

Vosk configuration for transcription
==================

To use [Vosk speech recognition server](https://github.com/alphacep/vosk-server)
start the server with a docker:

```
docker run -d -p 2700:2700 alphacep/kaldi-en:latest
```

Then configure the transcription class with the following properly in `~/jigasi/jigasi-home/sip-communicator.properties`:

```
org.jitsi.jigasi.transcription.customService=org.jitsi.jigasi.transcription.VoskTranscriptionService
```

Finally, configure the websocket URL of the VOSK service in `~/jigasi/jigasi-home/sip-communicator.properties`:

If you only have one instance of VOSK server:

```
# org.jitsi.jigasi.transcription.vosk.websocket_url=ws://localhost:2700
```

If you have multiple instances of VOSK for transcribing different languages, configure
the URLs of different VOSK instances in JSON format:
```
# org.jitsi.jigasi.transcription.vosk.websocket_url={"en": "ws://localhost:2700", "fr": "ws://localhost:2710"}
```

LibreTranslate configuration for translation
==================

To use [LibreTranslate](https://github.com/LibreTranslate/LibreTranslate)
for translation, configure the following properties in `~/jigasi/jigasi-home/sip-communicator.properties`:

```
org.jitsi.jigasi.transcription.translationService=org.jitsi.jigasi.transcription.LibreTranslateTranslationService
org.jitsi.jigasi.transcription.libreTranslate.api_url=http://localhost:5000/translate
```

Run the docker container along with Jigasi:
```
docker run -d -p 5000:5000 libretranslate/libretranslate
```
Note that by default, the LibreTranslate server downloads all language models
before starting to listen to requests. You may refer to the 
[documentation](https://github.com/LibreTranslate/LibreTranslate/blob/main/README.md)
to set up a volume or set the available languages to reduce download time.

Transcription options
=====================

There are several configuration options regarding transcription. These should
be placed in `~/jigasi/jigasi-home/sip-communicator.properties`. The default 
value will be used when the property is not set in the property file. A valid 
XMPP account must also be set to make Jigasi be able to join a conference room.
<table>
    <tr>
        <th>Property name</th>
        <th>Default value</th>
        <th>Description</th>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.DIRECTORY</td>
        <td>/var/lib/jigasi/transcripts</td>
        <td>The folder which will be used to store and serve the final 
            transcripts.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.BASE_URL</td>
        <td>http://localhost/</td>
        <td>The base URL which will be used to serve the final transcripts. 
            The URL used to serve a transcript will be this base appended by the
            filename of the transcript.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.jetty.port</td>
        <td>-1</td>
        <td>The port which will be used to serve the final transcripts. Its 
            default value is -1, which means the Jetty instance serving the 
            transcript files is turned off.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.ADVERTISE_URL</td>
        <td>false</td>
        <td>Whether or not to advertise the URL which will serve the final 
            transcript when Jigasi joins the room.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.SAVE_JSON</td>
        <td>false</td>
        <td>Whether or not to save the final transcript in JSON. Note that this
            format is not very human readable.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.SAVE_TXT</td>
        <td>true</td>
        <td>Whether or not to save the final transcript in plain text.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.SEND_JSON</td>
        <td>true</td>
        <td>Whether or not to send results, when they come in, to the chatroom 
            in JSON. Note that this will result in subtitles being shown.</td>
    </tr>
    <tr>
        <td>org.jitsi.jigasi.transcription.SEND_TXT</td>
        <td>false</td>
        <td>Whether or not to send results, when they come in, to the chatroom 
            in plain text. Note that this will result in the chat being somewhat
            spammed.</td>
    </tr>
</table>

Call control MUCs (brewery)
=======================================

For outgoing calls jigasi by default configures using a control room called brewery(XMPP MUC).
To configure using MUCs you need to add an XMPP account that will be used to 
connect to the XMPP server and add the property 
`org.jitsi.jigasi.BREWERY_ENABLED=true`.
Here are example XMPP account properties:
```
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1=acc-xmpp-1
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.ACCOUNT_UID=Jabber:jigasi@auth.meet.example.com
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.USER_ID=jigasi@auth.meet.example.com
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.IS_SERVER_OVERRIDDEN=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.SERVER_ADDRESS=<xmpp_server_ip_address>
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.SERVER_PORT=5222
#net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.BOSH_URL=https://xmpp_server_ip_address/http-bind
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.ALLOW_NON_SECURE=true
#base64 AES keyLength:256 or 128
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.PASSWORD=<xmpp_account_password>
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.AUTO_GENERATE_RESOURCE=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.RESOURCE_PRIORITY=30
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.KEEP_ALIVE_METHOD=XEP-0199
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.KEEP_ALIVE_INTERVAL=30
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.CALLING_DISABLED=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.JINGLE_NODES_ENABLED=false
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.IS_CARBON_DISABLED=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.DEFAULT_ENCRYPTION=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.IS_USE_ICE=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.IS_ACCOUNT_DISABLED=false
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.IS_PREFERRED_PROTOCOL=false
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.AUTO_DISCOVER_JINGLE_NODES=false
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.PROTOCOL=Jabber
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.IS_USE_UPNP=false
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.IM_DISABLED=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.SERVER_STORED_INFO_DISABLED=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.IS_FILE_TRANSFER_DISABLED=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.USE_DEFAULT_STUN_SERVER=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.ENCRYPTION_PROTOCOL.DTLS-SRTP=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.ENCRYPTION_PROTOCOL_STATUS.DTLS-SRTP=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.OVERRIDE_ENCODINGS=true
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.G722/8000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.GSM/8000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.H263-1998/90000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.H264/90000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.PCMA/8000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.PCMU/8000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.SILK/12000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.SILK/16000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.SILK/24000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.SILK/8000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.VP8/90000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.iLBC/8000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.opus/48000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.speex/16000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.speex/32000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.Encodings.speex/8000=0
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.BOSH_URL_PATTERN=https://{host}{subdomain}/http-bind?room={roomName}
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.DOMAIN_BASE=meet.example.com
net.java.sip.communicator.impl.protocol.jabber.acc-xmpp-1.BREWERY=JigasiBrewery@internal.auth.meet.example.com

```
The property `BOSH_URL_PATTERN` is the bosh URL that will be used from jigasi 
when a call on this account is received.

The value of `BREWERY` is the name of the brewery room where jigasi will connect.
That room needs to be configured in jicofo with the following property:
`org.jitsi.jicofo.jigasi.BREWERY=JigasiBrewery@internal.auth.meet.example.com` or in the new jicofo config:
`hocon -f /etc/jitsi/jicofo/jicofo.conf set jicofo.jigasi.brewery-jid '"JigasiBrewery@internal.auth.meet.example.com"'`
Where prosody needs to have a registered muc component: `internal.auth.meet.example.com`.

The configuration for the XMPP control MUCs that jigasi uses can be modified at 
run time using REST calls to `/configure/`.

# Adding an XMPP control MUC
A new XMPP control MUC can be added by posting a JSON which contains its configuration to `/configure/call-control-muc/add`:
```
{
  "id": "acc-xmpp-1",
  "ACCOUNT_UID":"Jabber:jigasi@auth.meet.example.com@meet.example.com",
  "USER_ID":"jigasi@auth.meet.example.com",
  "IS_SERVER_OVERRIDDEN":"true",
  .....
}
```

If a configuration with the specified ID already exists, the request will 
succeed (return 200), but the configuration will NOT be updated. If you need to 
update an existing configuration, you need to remove it first and then re-add it.

# Removing an XMPP control MUC.
An XMPP control MUC can be removed by posting a JSON which contains its ID 
to `/configure/call-control-muc/remove`:
```
{
  "id": "acc-xmpp-1"
}
```

The request will be successful (return 200) as long as the format of the JSON is 
as expected, and the connection was found and removed.
