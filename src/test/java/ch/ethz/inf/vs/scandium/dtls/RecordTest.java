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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.Before;
import org.junit.Test;

public class RecordTest {

	static final int SEQUENCE_NO = 5;
	static final int TYPE_APPL_DATA = 23;
	static final int EPOCH = 0;
	// byte representation of a 128 bit AES symmetric key
	static final byte[] aesKey = new byte[]{(byte) 0xC9, 0x0E, 0x6A, (byte) 0xA2, (byte) 0xEF, 0x60, 0x34, (byte) 0x96,
		(byte) 0x90, 0x54, (byte) 0xC4, (byte) 0x96, 0x65, (byte) 0xBA, 0x03, (byte) 0x9E};
	SecretKey key;
	
	DTLSSession session;
	byte[] payloadData;
	int payloadLength = 50;
	// salt: 32bit client write init vector (can be any four bytes)
	byte[] client_iv = new byte[]{0x55, 0x23, 0x2F, (byte) 0xA3};
	ProtocolVersion protocolVer;

	
	@Before
	public void setUp() throws Exception {
		
		protocolVer = new ProtocolVersion();
		key = new SecretKeySpec(aesKey, "AES");
		payloadData = new byte[payloadLength];
		for ( int i = 0; i < payloadLength; i++) {
			payloadData[i] = 0x34;
		}
		session = new DTLSSession(new InetSocketAddress("10.192.10.1", 7000), true);
		session.getReadState().setIv(new IvParameterSpec(client_iv));
		session.getReadState().setCipherSuite(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8);
		session.getReadState().setEncryptionKey(key);
	}

	@Test
	public void testFromByteArrayAcceptsKnownTypeCode() {
		
		byte[] application_record = newDTLSRecord(TYPE_APPL_DATA);
		List<Record> recordList = Record.fromByteArray(application_record);
		assertEquals(recordList.size(), 1);
		Record record = recordList.get(0);
		assertEquals(ContentType.APPLICATION_DATA, record.getType());
		assertEquals(EPOCH, record.getEpoch());
		assertEquals(SEQUENCE_NO, record.getSequenceNumber());
		assertEquals(protocolVer.getMajor(), record.getVersion().getMajor());
		assertEquals(protocolVer.getMinor(), record.getVersion().getMinor());
	}
	
	@Test
	public void testFromByteArrayRejectsUnknownTypeCode() {
		
		byte[] unsupported_dtls_record = newDTLSRecord(55);
		List<Record> recordList = Record.fromByteArray(unsupported_dtls_record);
		assertTrue(recordList.isEmpty());
	}
	
	/**
	 * Checks whether the {@link Record#decryptAEAD(byte[])} method uses the <em>explicit</em>
	 * nonce part included in the <i>GenericAEADCipher</i> struct instead of deriving the
	 * explicit nonce part frmo the epoch and sequence number contained in the <i>DTLSCiphertext</i>
	 * struct.
	 * 
	 * @throws HandshakeException if decryption fails
	 */
	@Test
	public void testDecryptAEADUsesExplicitNonceFromGenericAEADCipherStruct() throws HandshakeException {
		
		byte[] fragment = newGenericAEADCipherFragment();
		Record record = new Record(ContentType.APPLICATION_DATA, protocolVer, EPOCH, SEQUENCE_NO, fragment.length, fragment);
		record.setSession(session);
		
		byte[] decryptedData = record.decryptAEAD(fragment);
		assertTrue(Arrays.equals(decryptedData, payloadData));
	}
	
	private byte[] newGenericAEADCipherFragment() {
		// 64bit sequence number, consisting of 16bit epoch (0) + 48bit sequence number (5)
		byte[] seq_num = new byte[]{0x00, (byte) EPOCH, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) SEQUENCE_NO};
		
		// additional data based on sequence number, type (APPLICATION DATA) and protocol version
		byte[] additionalData = new byte[]{TYPE_APPL_DATA, (byte) protocolVer.getMajor(), (byte) protocolVer.getMinor(), 0, (byte) payloadLength};
		additionalData = ByteArrayUtils.concatenate(seq_num, additionalData);

		// "explicit" part of nonce, intentionally different from seq_num which MAY be used as the explicit nonce
		// but does not need to be used (at least that's my interpretation of the specs)
		byte[] explicitNonce = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		// nonce used for encryption, "implicit" part + "explicit" part
		byte[] nonce = ByteArrayUtils.concatenate(client_iv, explicitNonce);
		
		byte[] encryptedData = CCMBlockCipher.encrypt(key.getEncoded(), nonce, additionalData, payloadData, 8);
		
		// prepend the "explicit" part of nonce to the encrypted data to form the GenericAEADCipher struct
		return ByteArrayUtils.concatenate(explicitNonce, encryptedData);
	}

	private byte[] newDTLSRecord(int typeCode) {
		
		byte[] fragment = newGenericAEADCipherFragment();
		
		// the record header contains a type code, version, epoch, sequenceNo, length
		byte[] dtls_record_header = new byte[]{(byte) typeCode, (byte) protocolVer.getMajor(), (byte) protocolVer.getMinor(),
				0, EPOCH, 0, 0, 0, 0, 0, SEQUENCE_NO, 0, (byte) fragment.length};
		
		return ByteArrayUtils.concatenate(dtls_record_header, fragment);
	}
}
