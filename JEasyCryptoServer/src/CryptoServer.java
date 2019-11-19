import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;

//Java-Json Library
import org.json.JSONObject;
import org.json.JSONException;


import easycrypto.EasyCryptoAPI;

//https://github.com/stleary/JSON-java
// http://www.geeksforgeeks.org/parse-json-java/

public class CryptoServer implements Runnable {

	private DatagramSocket socket;
	private byte[] incoming;
	private boolean running = true;
	private static int port = 10000;

	public static void main(String[] args) {
        	if (args.length > 0) {
        		try {
				port = Integer.parseInt(args[0]);
 			} catch (NumberFormatException e) {
				System.out.printf("Error: The port ('%s') you entered is not valid. Please provide a valid port.\n", args[0]);
				System.out.println("Expected, e.g., './start.sh 3000'");
				System.out.println("Falling back to the default port of '10000' instead.");
 			}
                }
		new CryptoServer().run();
	}

	public void run() {
		try {
			System.out.printf("Launching CryptoServer (listening on port : %s)...\n", port);
			socket = new DatagramSocket(port);
			System.out.printf("Local addess is : %s\n", socket.getLocalAddress().getHostAddress());
			incoming = new byte[4096];
			DatagramPacket packet = new DatagramPacket(incoming, incoming.length);

			InetAddress sender = null;
			long id = 0;
			String response = null;
			String operation =  null;

			while (running) {
				try {
					System.out.println("Start to receive packets...");
					socket.receive(packet);
					System.out.println("Packet received!");
					String receivedData = new String(packet.getData(), 0, packet.getLength());
					sender = packet.getAddress();
					System.out.println("Sender is: " + sender.getHostAddress() + ":" + packet.getPort());
					System.out.println("Received raw data: " + receivedData);
					System.out.println("Parsing...");
					JSONObject root = new JSONObject(receivedData);

					id = Long.valueOf((Integer)(root.get("id"))); // id of the operation, for async operations.
					operation = (String) root.get("operation"); // request/response
					
					EasyCryptoAPI.Result result = null;
					
					if (operation.equalsIgnoreCase("encrypt")) {
						String method = (String) root.get("method"); // encrypt/decrypt
						String data = (String) root.get ("data"); // text to be handled
						result = EasyCryptoAPI.encrypt(data, method);
					} else if (operation.equalsIgnoreCase("decrypt")) {
						String method = (String) root.get("method"); // encrypt/decrypt
						String data = (String) root.get ("data"); // text to be handled
						result = EasyCryptoAPI.decrypt(data, method);
					} else if (operation.equalsIgnoreCase("capabilities")) {
						String methods = EasyCryptoAPI.methods();
						result = new EasyCryptoAPI.Result(EasyCryptoAPI.ResultCode.ESuccess, methods);
					} else {
						result = new EasyCryptoAPI.Result(EasyCryptoAPI.ResultCode.ENotSupported, "Operation not supported");
					}
					response = createResponse(operation, id, result);
				} catch (IOException ioe) {
					ioe.printStackTrace();
					response = createResponse(operation, id, new EasyCryptoAPI.Result(EasyCryptoAPI.ResultCode.EError, ioe.getLocalizedMessage()));
				} catch (JSONException e) {
					e.printStackTrace();
					response = createResponse(operation, id, new EasyCryptoAPI.Result(EasyCryptoAPI.ResultCode.EError, e.getLocalizedMessage()));
				} finally {
					if (null != response && null != sender) {
						System.out.println("Sending response: " + response);
						DatagramPacket sendPacket = new DatagramPacket(response.getBytes("UTF-8"), response.getBytes().length);
						sendPacket.setAddress(sender);
						sendPacket.setPort(packet.getPort());
						try {
							socket.send(sendPacket);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				
			}
		} catch (SocketException | UnsupportedEncodingException e) {
			e.printStackTrace();
			
		} finally {
			if (null != socket) {
				socket.close();
				socket = null;
			}
		}
	} // end run()
	
	private String createResponse(String op, long id, EasyCryptoAPI.Result result) {
		HashMap<String, Object> responseMap = new HashMap<>();
		responseMap.put("id", id);
		responseMap.put("operation", op + "-response");
		responseMap.put("result", result.resultCode().ordinal());
		responseMap.put("data", result.result());
		JSONObject ResponseJsonObject = new JSONObject(responseMap);
		String responseString = ResponseJsonObject.toString();

		return responseString;
	}
}
