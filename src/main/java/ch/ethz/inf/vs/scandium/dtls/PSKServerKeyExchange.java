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
package ch.ethz.inf.vs.scandium.dtls;

import java.io.UnsupportedEncodingException;

import ch.ethz.inf.vs.scandium.util.DatagramReader;
import ch.ethz.inf.vs.scandium.util.DatagramWriter;

/**
 * The key exchange message sent when using the preshared key key exchange
 * algorithm. To help the client in selecting which identity to use, the server
 * can provide a "PSK identity hint" in the ServerKeyExchange message. If no
 * hint is provided, the ServerKeyExchange message is omitted. See <a
 * href="http://tools.ietf.org/html/rfc4279#section-2">ServerKeyExchange</a> for
 * the message format.
 * 
 * @author Jucker
 * 
 */
public class PSKServerKeyExchange extends ServerKeyExchange {

	// DTLS-specific constants ////////////////////////////////////////

	private static final int IDENTITY_HINT_LENGTH_BITS = 16;
	
	private static final String CHAR_SET = "UTF8";

	// Members ////////////////////////////////////////////////////////

	/**
	 * The PSK identity MUST be first converted to a character string, and then
	 * encoded to octets using UTF-8. See <a
	 * href="http://tools.ietf.org/html/rfc4279#section-5.1">RFC 4279</a>.
	 */
	private byte[] hintEncoded;

	/** The hint in cleartext. */
	private String hint;

	// Constructors ///////////////////////////////////////////////////
	
	public PSKServerKeyExchange(String hint) {
		this.hint = hint;
		try {
			this.hintEncoded = hint.getBytes(CHAR_SET);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	public PSKServerKeyExchange(byte[] hintEncoded) {
		this.hintEncoded = hintEncoded;
		try {
			this.hint = new String(hintEncoded, CHAR_SET);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	// Methods ////////////////////////////////////////////////////////

	@Override
	public int getMessageLength() {
		// fixed: 2 bytes for the length field
		// http://tools.ietf.org/html/rfc4279#section-2: opaque psk_identity_hint<0..2^16-1>;
		return 2 + hintEncoded.length;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(super.toString());
		sb.append("\t\tPSK Identity Hint: " + hint + "\n");

		return sb.toString();
	}

	// Serialization //////////////////////////////////////////////////

	@Override
	public byte[] fragmentToByteArray() {
		DatagramWriter writer = new DatagramWriter();
		
		writer.write(hintEncoded.length, IDENTITY_HINT_LENGTH_BITS);
		writer.writeBytes(hintEncoded);
		
		return writer.toByteArray();
	}
	
	public static HandshakeMessage fromByteArray(byte[] byteArray) {
		DatagramReader reader = new DatagramReader(byteArray);
		
		int length = reader.read(IDENTITY_HINT_LENGTH_BITS);
		byte[] hintEncoded = reader.readBytes(length);
		
		return new PSKServerKeyExchange(hintEncoded);
	}
	
	// Getters and Setters ////////////////////////////////////////////

	public String getHint() {
		return hint;
	}

	public void setHint(String hint) {
		this.hint = hint;
	}

}
