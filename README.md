Scandium (Sc) Security for Californium
======================================

[![Build Status](https://api.travis-ci.org/mkovatsc/Scandium.png?branch=master)](https://travis-ci.org/mkovatsc/Scandium)

Scandium (Sc) is a DTLS 1.2 implementation for the Californium (Cf) CoAP framework.
It uses the [element-connector](https://github.com/mkovatsc/element-connector)
interface, which is a socket-like API to send and receive raw data and allows
the modularization of Californium (Cf). Hence, Scandium (Sc) can also be used
standalone, i.e. without Cf's CoAP implementation on top.

Maven
-----

Use `mvn clean install` in the Cf root directory to build everything.
Standalone JARs of the examples will be copied to ./run/.
(For convenience they are directly included in the Git repository.)

The Maven repositories are:

* [https://github.com/mkovatsc/maven/raw/master/releases/](https://github.com/mkovatsc/maven/raw/master/releases/)
* [https://github.com/mkovatsc/maven/raw/master/snapshots/](https://github.com/mkovatsc/maven/raw/master/snapshots/)

Eclipse
-------

The project also includes the project files for Eclipse. Make sure to have the
following before importing the Californium (Cf) projects:

* [Eclipse EGit](http://www.eclipse.org/egit/)
* [m2e - Maven Integration for Eclipse](http://www.eclipse.org/m2e/)
* UTF-8 workspace text file encoding (Preferences &raquo; General &raquo; Workspace)

Then choose *[Import... &raquo; Git &raquo; Projects from Git &raquo; Local]*
to import Californium into Eclipse.

Included Certificates
---------------------

The sub-directory `certs` contains the Java key stores to run Scandium (Sc).

### Trust Store

*	Contains the self-signed root CA: *Cf Root CA*
*	Password: `rootPass`

### Key Store

*	Contains the certificate chain for DTLS endpoints: *Cf Client CA* and *Cf Server CA* 
*	Password: `endPass`

Creating Certificates
---------------------

Having OpenSSL installed, certificates and key stores can be created with the
following steps:

	# Create private key and self-signed root CA
	openssl ecparam -name prime256v1 -genkey -out root.key
	openssl req -new -key root.key -x509 -sha256 -days 365 -out root.crt
	
	# Create private key, signing request, and sign with root CA
	openssl ecparam -name prime256v1 -genkey -out inter.key
	openssl req -new -key inter.key -sha256 -out inter.csr
	openssl x509 -sha256 -req -in inter.csr -CA root.crt -CAkey root.key -out inter.crt -days 365 -CAcreateserial
	
	# Import root CA into Java's trusted CAs
	keytool -importcert -alias californium -file root.crt -keystore "$JAVA_HOME/jre/lib/security/cacerts"
	
	# Import root CA into portable trust store
	keytool -importcert -alias root -file root.crt -keystore trustStore.jks
	
	# Create client CA and import certificate chain into key store
	keytool -genkeypair -alias client -keyalg EC -keystore keyStore.jks -sigalg SHA256withECDSA -validity 365
	keytool -certreq -alias client -keystore keyStore.jks -file client.csr
	openssl x509 -req -in client.csr -CA inter.crt -CAkey inter.key -out client.crt -sha256 -days 365 -CAcreateserial
	keytool -importcert -alias inter -file inter.crt -keystore keyStore.jks -trustcacerts
	keytool -importcert -alias client -file client.crt -keystore keyStore.jks -trustcacerts
	
	# Create server CA and import certificate chain into key store
	keytool -genkeypair -alias server -keyalg EC -keystore keyStore.jks -sigalg SHA256withECDSA -validity 365
	keytool -certreq -alias server -keystore keyStore.jks -file server.csr
	openssl x509 -req -in server.csr -CA inter.crt -CAkey inter.key -out server.crt -sha256 -days 365 -CAcreateserial
	keytool -importcert -alias server -file server.crt -keystore keyStore.jks -trustcacerts
	
	# List certificate chain in key store
	keytool -list -v -keystore keyStore.jks