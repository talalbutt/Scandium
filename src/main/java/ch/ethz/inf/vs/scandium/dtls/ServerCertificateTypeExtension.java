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

import java.util.ArrayList;
import java.util.List;

import ch.ethz.inf.vs.scandium.dtls.HelloExtensions.ExtensionType;
import ch.ethz.inf.vs.scandium.util.DatagramReader;

public class ServerCertificateTypeExtension extends CertificateTypeExtension {

	// Constructors ///////////////////////////////////////////////////
	
	/**
	 * Constructs an empty certificate type extension. If it is client-sided
	 * there is a list of supported certificate type (ordered by preference);
	 * server-side only 1 certificate type is chosen.
	 * 
	 * @param isClient
	 *            whether this instance is considered the client.
	 */
	public ServerCertificateTypeExtension(boolean isClient) {
		super(ExtensionType.SERVER_CERT_TYPE, isClient);
	}
	
	/**
	 * Constructs a certificate type extension with a list of supported
	 * certificate types. The server only chooses 1 certificate type.
	 * 
	 * @param certificateTypes
	 *            the list of supported certificate types.
	 * @param isClient
	 *            whether this instance is considered the client.
	 */
	public ServerCertificateTypeExtension(boolean isClient, List<CertificateType> certificateTypes) {
		super(ExtensionType.SERVER_CERT_TYPE, isClient, certificateTypes);
	}

	// Methods ////////////////////////////////////////////////////////

	public String toString() {
		StringBuilder sb = new StringBuilder(super.toString());

		for (CertificateType type : certificateTypes) {
			sb.append("\t\t\t\tServer certificate type: " + type.toString() + "\n");
		}

		return sb.toString();
	};
	
	public static HelloExtension fromByteArray(byte[] byteArray) {
		DatagramReader reader = new DatagramReader(byteArray);
		
		List<CertificateType> certificateTypes = new ArrayList<CertificateType>();
		
		// the client's extension needs at least 2 bytes, while the server's is exactly 1 byte long
		boolean isClientExtension = true;
		if (byteArray.length > 1) {
			int length = reader.read(LIST_FIELD_LENGTH_BITS);
			for (int i = 0; i < length; i++) {
				certificateTypes.add(CertificateType.getTypeFromCode(reader.read(EXTENSION_TYPE_BITS)));
			}
		} else {
			certificateTypes.add(CertificateType.getTypeFromCode(reader.read(EXTENSION_TYPE_BITS)));
			isClientExtension = false;
		}

		return new ServerCertificateTypeExtension(isClientExtension, certificateTypes);
	}

}
