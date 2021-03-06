/*******************************************************************************
 * Copyright (c) 2014, Institute for Pervasive Computing, ETH Zurich.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * 
 * This file is part of the Scandium (Sc) Security for Californium.
 ******************************************************************************/
package ch.ethz.inf.vs.scandium;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import ch.ethz.inf.vs.elements.ConnectorBase;
import ch.ethz.inf.vs.elements.RawData;
import ch.ethz.inf.vs.scandium.dtls.AlertMessage;
import ch.ethz.inf.vs.scandium.dtls.AlertMessage.AlertDescription;
import ch.ethz.inf.vs.scandium.dtls.AlertMessage.AlertLevel;
import ch.ethz.inf.vs.scandium.dtls.ApplicationMessage;
import ch.ethz.inf.vs.scandium.dtls.ByteArrayUtils;
import ch.ethz.inf.vs.scandium.dtls.ClientHandshaker;
import ch.ethz.inf.vs.scandium.dtls.ClientHello;
import ch.ethz.inf.vs.scandium.dtls.ContentType;
import ch.ethz.inf.vs.scandium.dtls.DTLSFlight;
import ch.ethz.inf.vs.scandium.dtls.DTLSMessage;
import ch.ethz.inf.vs.scandium.dtls.DTLSSession;
import ch.ethz.inf.vs.scandium.dtls.FragmentedHandshakeMessage;
import ch.ethz.inf.vs.scandium.dtls.HandshakeException;
import ch.ethz.inf.vs.scandium.dtls.HandshakeMessage;
import ch.ethz.inf.vs.scandium.dtls.Handshaker;
import ch.ethz.inf.vs.scandium.dtls.Record;
import ch.ethz.inf.vs.scandium.dtls.ResumingClientHandshaker;
import ch.ethz.inf.vs.scandium.dtls.ResumingServerHandshaker;
import ch.ethz.inf.vs.scandium.dtls.ServerHandshaker;
import ch.ethz.inf.vs.scandium.dtls.ServerHello;
import ch.ethz.inf.vs.scandium.util.ScProperties;

public class DTLSConnector extends ConnectorBase {
	
	/*
	 * Note: DTLSConnector can also implement the interface Connector instead of
	 * extending ConnectorBase
	 */
	
	private final static Logger LOGGER = Logger.getLogger(DTLSConnector.class.getCanonicalName());

	public static final String KEY_STORE_LOCATION = ScProperties.std.getProperty("KEY_STORE_LOCATION".replace("/", File.pathSeparator));
	public static final String TRUST_STORE_LOCATION = ScProperties.std.getProperty("TRUST_STORE_LOCATION".replace("/", File.pathSeparator));
	
	private int maxFragmentLength = ScProperties.std.getInt("MAX_FRAGMENT_LENGTH");
	
	/** The overhead for the record header (13 bytes) and the handshake header (12 bytes) is 25 bytes */
	private int maxPayloadSize = maxFragmentLength + 25;

	/** The initial timer value for retransmission; rfc6347, section: 4.2.4.1 */
	private int retransmission_timeout = ScProperties.std.getInt("RETRANSMISSION_TIMEOUT");
	
	/** Maximal number of retransmissions before the attempt to transmit a message is canceled */
	private int max_retransmit = ScProperties.std.getInt("MAX_RETRANSMIT");
	
	private final InetSocketAddress address;
	
	private DatagramSocket socket;
	
	/** The timer daemon to schedule retransmissions. */
	private Timer timer = new Timer(true); // run as daemon
	
	/** Storing sessions according to peer-addresses */
	private ConcurrentHashMap<String, DTLSSession> dtlsSessions = new ConcurrentHashMap<String, DTLSSession>();

	/** Storing handshakers according to peer-addresses. */
	private ConcurrentHashMap<String, Handshaker> handshakers = new ConcurrentHashMap<String, Handshaker>();

	/** Storing flights according to peer-addresses. */
	private ConcurrentHashMap<String, DTLSFlight> flights = new ConcurrentHashMap<String, DTLSFlight>();
	
	public DTLSConnector(InetSocketAddress address) {
		super(address);
		this.address = address;
	}
	
	/**
	 * Close the DTLS session with all peers.
	 */
	public void close() {
		for (DTLSSession session : dtlsSessions.values()) {
			this.close(session.getPeer());
		}
	}
	
	/**
	 * Close the DTLS session with the given peer.
	 * 
	 * @param peerAddress the remote endpoint of the session to close
	 */
	public void close(InetSocketAddress peerAddress) {
		DTLSSession session = dtlsSessions.get(addressToKey(peerAddress));
		
		if (session != null) {
			DTLSMessage closeNotify = new AlertMessage(AlertLevel.WARNING, AlertDescription.CLOSE_NOTIFY);
			
			DTLSFlight flight = new DTLSFlight();
			flight.addMessage(new Record(ContentType.ALERT, session.getWriteEpoch(), session.getSequenceNumber(), closeNotify, session));
			flight.setRetransmissionNeeded(false);
			
			cancelPreviousFlight(peerAddress);
	
			flight.setPeerAddress(peerAddress);
			flight.setSession(session);
	
			LOGGER.fine("Sending CLOSE_NOTIFY to " + peerAddress.toString());
			
			sendFlight(flight);
		} else {
			LOGGER.warning("Session to close not found: " + peerAddress.toString());
		}
	}
	
	@Override
	public synchronized void start() throws IOException {
		socket = new DatagramSocket(address.getPort(), address.getAddress());
		super.start();
		
		LOGGER.info("DLTS connector listening on "+address);
	}
	
	@Override
	public synchronized void stop() {
		this.close();
		this.socket.close();
		super.stop();
	}
	
	// TODO: We should not return null
	@Override
	protected RawData receiveNext() throws Exception {
		byte[] buffer = new byte[maxPayloadSize];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socket.receive(packet);
		
		if (packet.getLength() == 0)
			return null;

		InetSocketAddress peerAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
		LOGGER.finest(" => find handshaker for key "+peerAddress.toString());
		DTLSSession session = dtlsSessions.get(addressToKey(peerAddress));
		Handshaker handshaker = handshakers.get(addressToKey(peerAddress));
		byte[] data = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

		try {
			List<Record> records = Record.fromByteArray(data);

			for (Record record : records) {
				record.setSession(session);

				RawData raw = null;

				ContentType contentType = record.getType();
				LOGGER.finest(" => contentType: "+contentType);
				DTLSFlight flight = null;
				switch (contentType) {
				case APPLICATION_DATA:
					if (session == null) {
						// There is no session available, so no application data
						// should be received, discard it
						LOGGER.info("Discarded unexpected application data message from " + peerAddress.toString());
						return null;
					}
					// at this point, the current handshaker is not needed
					// anymore, remove it
					handshakers.remove(addressToKey(peerAddress));

					ApplicationMessage applicationData = (ApplicationMessage) record.getFragment();
					raw = new RawData(applicationData.getData());
					break;

				case ALERT:
					AlertMessage alert = (AlertMessage) record.getFragment();
					switch (alert.getDescription()) {
					case CLOSE_NOTIFY:
						session.setActive(false);
						
						LOGGER.fine("Received CLOSE_NOTIFY from " + peerAddress.toString());
						
						// server must reply with CLOSE_NOTIFY
						if (!session.isClient()) {
							DTLSMessage closeNotify = new AlertMessage(AlertLevel.WARNING, AlertDescription.CLOSE_NOTIFY);
							flight = new DTLSFlight();
							flight.addMessage(new Record(ContentType.ALERT, session.getWriteEpoch(), session.getSequenceNumber(), closeNotify, session));
							flight.setRetransmissionNeeded(false);
						}
						
						if (dtlsSessions.remove(addressToKey(peerAddress))!=null) {
							LOGGER.info("Closed session with peer: " + peerAddress.toString());
						} else {
							LOGGER.warning("Session to close not found: " + peerAddress.toString());
						}
						
						break;
					
					// remote implementation might use any alert (e.g., against padding oracle attack)
					default:
						LOGGER.warning(alert.getDescription() + " with " + peerAddress.toString());

						// cleaning up
						cancelPreviousFlight(peerAddress);
						dtlsSessions.remove(addressToKey(peerAddress));
						handshakers.remove(addressToKey(peerAddress));
						break;
						
						//TODO somehow tell CoAP endpoint to cancel
					}
					break;
				case CHANGE_CIPHER_SPEC:
				case HANDSHAKE:
					LOGGER.finest(" => handshaker: "+handshaker);
					if (handshaker == null) {
						/*
						 * A handshake message received, but no handshaker
						 * available: this must mean that we either received
						 * a HelloRequest (from server) or a ClientHello
						 * (from client) => initialize appropriate
						 * handshaker type
						 */

						HandshakeMessage handshake = (HandshakeMessage) record.getFragment();

						switch (handshake.getMessageType()) {
						case HELLO_REQUEST:
							/*
							 * Client side: server desires a re-handshake
							 */
							if (session == null) {
								// create new session
								session = new DTLSSession(peerAddress, true);
								// store session according to peer address
								dtlsSessions.put(addressToKey(peerAddress), session);

								LOGGER.info("Created new session as client with peer: " + peerAddress.toString());
							}
							handshaker = new ClientHandshaker(peerAddress, null, session);
							handshakers.put(addressToKey(peerAddress), handshaker);
							LOGGER.finest("Stored re-handshaker: " + handshaker.toString() + " for " + peerAddress.toString());
							break;

						case CLIENT_HELLO:
							/*
							 * Server side: server received a client hello:
							 * check first if client wants to resume a
							 * session (message must contain session
							 * identifier) and then check if particular
							 * session still available, otherwise conduct
							 * full handshake with fresh session.
							 */

							if (!(handshake instanceof FragmentedHandshakeMessage)) {
								// check if session identifier set
								ClientHello clientHello = (ClientHello) handshake;
								session = getSessionByIdentifier(clientHello.getSessionId().getSessionId());
							}
							
							if (session == null) {
								// create new session
								session = new DTLSSession(peerAddress, false);
								// store session according to peer address
								dtlsSessions.put(addressToKey(peerAddress), session);

								LOGGER.info("Created new session as server with peer: " + peerAddress.toString());
								handshaker = new ServerHandshaker(peerAddress, session);
							} else {
								handshaker = new ResumingServerHandshaker(peerAddress, session);
							}
							handshakers.put(addressToKey(peerAddress), handshaker);
							LOGGER.finest("Stored handshaker: " + handshaker.toString() + " for " + peerAddress.toString());
							break;

						default:
							LOGGER.severe("Received unexpected first handshake message (type="+handshake.getMessageType()+") from " + peerAddress.toString() + ":\n" + handshake.toString());
							break;
						}
					}
					flight = handshaker.processMessage(record);
					break;

				default:
					LOGGER.severe("Received unknown DTLS record from " + peerAddress.toString() + ":\n" + ByteArrayUtils.toHexString(data));
					break;
				}

				if (flight != null) {
					cancelPreviousFlight(peerAddress);

					flight.setPeerAddress(peerAddress);
					flight.setSession(session);

					if (flight.isRetransmissionNeeded()) {
						flights.put(addressToKey(peerAddress), flight);
						scheduleRetransmission(flight);
					}

					sendFlight(flight);
				}

				if (raw != null) {

					raw.setAddress(packet.getAddress());
					raw.setPort(packet.getPort());

					return raw;
				}
			}

		} catch (Exception e) {
			/*
			 * If it is a known handshake failure, send the specific Alert,
			 * otherwise the general Handshake_Failure Alert. 
			 */
			DTLSFlight flight = new DTLSFlight();
			flight.setRetransmissionNeeded(false);
			flight.setPeerAddress(peerAddress);
			flight.setSession(session);
			
			AlertMessage alert;
			if (e instanceof HandshakeException) {
				alert = ((HandshakeException) e).getAlert();
				LOGGER.severe("Handshake Exception (" + peerAddress.toString() + "): " + e.getMessage());
			} else {
				alert = new AlertMessage(AlertLevel.FATAL, AlertDescription.HANDSHAKE_FAILURE);
				LOGGER.log(Level.SEVERE, "Unknown Exception (" + peerAddress + ").", e);
			}

			LOGGER.log(Level.SEVERE, "Datagram which lead to exception (" + peerAddress + "): " + ByteArrayUtils.toHexString(data), e);
			
			if (session == null) {
				// if the first received message failed, no session has been set
				session = new DTLSSession(peerAddress, false);
			}
			cancelPreviousFlight(peerAddress);
			
			flight.addMessage(new Record(ContentType.ALERT, session.getWriteEpoch(), session.getSequenceNumber(), alert, session));
			sendFlight(flight);
		} // receive()
		return null;
	}

	@Override
	protected void sendNext(RawData message) throws Exception {
		
		InetSocketAddress peerAddress = message.getInetSocketAddress();
		LOGGER.fine("Sending message to " + peerAddress);

		DTLSSession session = dtlsSessions.get(addressToKey(peerAddress));
		
		/*
		 * When the DTLS layer receives a message from an upper layer, there is
		 * either a already a DTLS session available with the peer or a new
		 * handshake must be executed. If a session is available and active, the
		 * message will be encrypted and send to the peer, otherwise a short
		 * handshake will be initiated.
		 */
		Record encryptedMessage = null;
		Handshaker handshaker = null;

		if (session == null) {
			// no session with endpoint available, create new empty session,
			// start fresh handshake
			session = new DTLSSession(peerAddress, true);
			dtlsSessions.put(addressToKey(peerAddress), session);
			handshaker = new ClientHandshaker(peerAddress, message, session);

		} else {

			if (session.isActive()) {
				// session to peer is active, send encrypted message
				DTLSMessage fragment = new ApplicationMessage(message.getBytes());
				encryptedMessage = new Record(ContentType.APPLICATION_DATA, session.getWriteEpoch(), session.getSequenceNumber(), fragment, session);
				
			} else {
				// try resuming session
				handshaker = new ResumingClientHandshaker(peerAddress, message, session);
			}
		}
		
		DTLSFlight flight = new DTLSFlight();
		// the CoAP message can not be encrypted since no session with peer
		// available, start DTLS handshake protocol
		if (handshaker != null) {
			// get starting handshake message
			handshakers.put(addressToKey(peerAddress), handshaker);
			LOGGER.finest("Stored handshaker on send: " + handshaker.toString() + " for " + peerAddress.toString());

			flight = handshaker.getStartHandshakeMessage();
			flights.put(addressToKey(peerAddress), flight);
			scheduleRetransmission(flight);
		}
		
		// the CoAP message has been encrypted and can be sent to the peer
		if (encryptedMessage != null) {
			flight.addMessage(encryptedMessage);
		}
		
		flight.setPeerAddress(peerAddress);
		flight.setSession(session);
		sendFlight(flight);
	}
	
	/**
	 * Searches through all stored sessions and returns that session which
	 * matches the session identifier or <code>null</code> if no such session
	 * available. This method is used when the server receives a
	 * {@link ClientHello} containing a session identifier indicating that the
	 * client wants to resume a previous session. If a matching session is
	 * found, the server will resume the session with a abbreviated handshake,
	 * otherwise a full handshake (with new session identifier in
	 * {@link ServerHello}) is conducted.
	 * 
	 * @param sessionID
	 *            the client's session identifier.
	 * @return the session which matches the session identifier or
	 *         <code>null</code> if no such session exists.
	 */
	private DTLSSession getSessionByIdentifier(byte[] sessionID) {
		if (sessionID == null) {
			return null;
		}
		
		for (Entry<String, DTLSSession> entry : dtlsSessions.entrySet()) {
			// FIXME session identifiers may not be set, when the handshake failed after the initial message
			// these sessions must be deleted when this happens
			try {
				byte[] id = entry.getValue().getSessionIdentifier().getSessionId();
				if (Arrays.equals(sessionID, id)) {
					return entry.getValue();
				}
			} catch (Exception e) {
				continue;
			}
		}
		
		for (DTLSSession session:dtlsSessions.values()) {
			try {
				byte[] id = session.getSessionIdentifier().getSessionId();
				if (Arrays.equals(sessionID, id)) {
					return session;
				}
			} catch (Exception e) {
				continue;
			}
		}
		
		return null;
	}
	
	private void sendFlight(DTLSFlight flight) {
		byte[] payload = new byte[] {};
		
		// put as many records into one datagram as allowed by the block size
		List<DatagramPacket> datagrams = new ArrayList<DatagramPacket>();

		for (Record record : flight.getMessages()) {
			if (flight.getTries() > 0) {
				// adjust the record sequence number
				int epoch = record.getEpoch();
				record.setSequenceNumber(flight.getSession().getSequenceNumber(epoch));
			}
			
			byte[] recordBytes = record.toByteArray();
			if (payload.length + recordBytes.length > maxPayloadSize) {
				// can't add the next record, send current payload as datagram
				DatagramPacket datagram = new DatagramPacket(payload, payload.length, flight.getPeerAddress().getAddress(), flight.getPeerAddress().getPort());
				datagrams.add(datagram);
				payload = new byte[] {};
			}

			// retrieve payload
			payload = ByteArrayUtils.concatenate(payload, recordBytes);
		}
		DatagramPacket datagram = new DatagramPacket(payload, payload.length, flight.getPeerAddress().getAddress(), flight.getPeerAddress().getPort());
		datagrams.add(datagram);

		// send it over the UDP socket
		try {
			for (DatagramPacket datagramPacket : datagrams) {
				socket.send(datagramPacket);
			}
			
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Could not send the datagram", e);
		}
	}
	
	private void handleTimeout(DTLSFlight flight) {

		// set DTLS retransmission maximum
		final int max = max_retransmit;

		// check if limit of retransmissions reached
		if (flight.getTries() < max) {

			flight.incrementTries();

			sendFlight(flight);

			// schedule next retransmission
			scheduleRetransmission(flight);

		} else {
			LOGGER.fine("Maximum retransmissions reached.");
		}
	}

	private void scheduleRetransmission(DTLSFlight flight) {

		// cancel existing schedule (if any)
		if (flight.getRetransmitTask() != null) {
			flight.getRetransmitTask().cancel();
		}
		
		if (flight.isRetransmissionNeeded()) {
			// create new retransmission task
			flight.setRetransmitTask(new RetransmitTask(flight));
	
			// calculate timeout using exponential back-off
			if (flight.getTimeout() == 0) {
				// use initial timeout
				flight.setTimeout(retransmission_timeout);
			} else {
				// double timeout
				flight.incrementTimeout();
			}
	
			// schedule retransmission task
			timer.schedule(flight.getRetransmitTask(), flight.getTimeout());
		}
	}
	
	/**
	 * Cancels the retransmission timer of the previous flight (if available).
	 * 
	 * @param peerAddress
	 *            the peer's address.
	 */
	private void cancelPreviousFlight(InetSocketAddress peerAddress) {
		DTLSFlight previousFlight = flights.get(addressToKey(peerAddress));
		if (previousFlight != null) {
			previousFlight.getRetransmitTask().cancel();
			previousFlight.setRetransmitTask(null);
			flights.remove(addressToKey(peerAddress));
		}
	}

	@Override
	public String getName() {
		return "DTLS";
	}

	public InetSocketAddress getAddress() {
		if (socket == null) return getLocalAddr();
		else return new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort());
	}
	
	private class RetransmitTask extends TimerTask {

		private DTLSFlight flight;

		RetransmitTask(DTLSFlight flight) {
			this.flight = flight;
		}

		@Override
		public void run() {
			handleTimeout(flight);
		}
	}
	
	private String addressToKey(InetSocketAddress address) {
		return address.toString().split("/")[1];
	}

}
