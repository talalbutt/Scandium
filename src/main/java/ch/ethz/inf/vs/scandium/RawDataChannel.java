package ch.ethz.inf.vs.scandium;

/**
 * This is the interface needed between a CoAP stack and a connector. The
 * connector forwards raw data to the method receiveData() and the CoAP stack
 * forwards messages to the corresponding method sendX(). {@link Endpoint} uses
 * this interface to connect {@link CoapStack} to {@link UDPConnector}.
 */
public interface RawDataChannel {

	/**
	 * Sends raw Data.
	 * 
	 * @param raw
	 *            the data
	 */
	public void sendData(RawData raw);

	/**
	 * Forwards the specified data to the stack. First, they must be parsed to a
	 * {@link Request}, {@link Response} or {@link EmptyMessage}. Second, the
	 * matcher finds the corresponding exchange and finally, the stack will
	 * process the message.
	 * 
	 * @param raw
	 *            the raw data
	 */
	public void receiveData(RawData raw);

}
