# Build Your Own ginlo Client

First, and foremost: Thank you for your interest in the ginlo client. We hope you enjoy looking into the
code and building a client for yourself.

The code was (and is) originally written in Java and later partially supplemented with Kotlin so you find both.

Since this code has a lot of [history](HISTORY.md), you may recognize varying "flavours" by different developers.
There are only a few comments and less documentation - often in German language. Until now no rewriting or optimization
has been done, except bugfixing where we had to.

The original idea behind the code was to develop a simple messenger; then the developers added the ability
to have different clients (called "Mandant" in the code, from German, meaning Client). Thus you will find
a lot of "isBaMandant" (BA = Business Application = ginlo Business). 

The code is more than 85% shared between Ginlo Private and Ginlo Business. There are some minor differences that
you'll find implemented using something like:

    if(isBaMandant()) {
    }

Additionally, the code supports Mobile Device Management (MDM), but only for the business version (obviously). 
Thus you will find, quite a few times, `*MDM*`.

Since ginlo.net GmbH only releases the ginlo Private *configuration* into the public you are ready to build a 
private version. Thus you can just ignore the MDM-stuff. 

But note: some features are available only for the business version. These include **Managed Groups** and
**Restricted Groups**. The clients can not create these groups. These groups can only be created by
the business administration cockpit, which only business customers have access to.

If you want to build and run your own client and you are not member of either the ginlo.net GmbH development team 
*or* the CDSK e.V. development team, you need to adjust some items to be able to compile and run this.

## Installation / Configuration

1. Install Pre-Requisities
2. Clone the Repo (`develop` is our main branch)
3. Set up app/gradle.config
4. Build

## 1. Install Pre-Requisities

Best would be using Android Studio. That does the trick.
Now you can go the step 2: Clone the Repo.

## 2. Clone the Repo

Go to the repository's page on GitHub, get the URL (cloning) and clone it onto your harddisk.

## 3. Set up gradle config

### In the section "App constants":

`applicationId 'eu.ginlo_apps.ginlo'`
Choose something different for yourself to not collide with regular ginlo clients on your device.

### In the section "Server and api constants and settings" 

`buildConfigField("String", "GINLO_BASE_URL", '""')`

You need to obtain this entry from ginlo.net GmbH in order to run your client against their backend.

`buildConfigField("String", "GINLO_AVC_SERVER_URL", '""')`

If you want your client to be able to **initiate** AV-calls, you will need to run your own JitsiMeet
Server. Please enter the url of your JitsiMeet-Server

`buildConfigField("String", "APPLICATION_KEYSTORE_DATA", '"KEYSTORE_DATA"')`

Must generate and have a cert chain in UBER keystore format as Base64 string here to use certificate
pinning for the server backend.

`buildConfigField("String", "APPLICATION_KEYSTORE_PASS", '"KEYSTORE_PASS"')`

`buildConfigField("String", "CERT_PINNING_CA_PW", '"PINNING_CA_PW"')`

Set these accordingly.

`buildConfigField("String", "PUBLICKEY_SYSTEM_CHAT", '"<RSAKeyValue><Modulus>RSA_KEY_MODULUS</Modulus><Exponent>RSA_KEY_EXPONENT</Exponent></RSAKeyValue>"')`

You need to obtain this entry from ginlo.net GmbH in order to run your client against their backend.

### Finalize Configuration

In order to receive message notifications while being idled by Android, ginlo uses the Firebase Cloud Messaging services (FCM).
You find the appropriate libraries for these and need a firebase account for setting up the google-service.json which is needed
to sucessfully build the app. But even if you manage to have that set up, the ginlo servers don't know about you, so you will
not receive any FCM messages. There are two ways to get around this:

1. The ginlo app uses long poll to keep it's message database up-to-date. So if you prevent the app from falling asleep on your
device you receive new messages every time they arrive. But you must then remove the Google libraries from your code and
configuration, to get rid of the build errors.
2. CDSK e.V. is supported by ginlo.net GmbH and has a valid FCM-API-configuration at hand. You may contribute to the community
project and ...

## 4. Build

You may now build a debug or release version of ginlo Private and take it to your device or the simulator.
Please note, that you need your own keystore for building a release apk. The debug keystore, which is created automatically
by Android Studio, is not suitable for releases.

## Good Luck and Godspeed

If you need any help you can open an issue and mention the maintainer(s).

Until then: Good Luck and Godspeed.

## Copyright

This repository is Copyright (c) 2020,2021 ginlo.net GmbH


