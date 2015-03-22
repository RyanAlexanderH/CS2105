import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

class FileSender {

	public DatagramSocket socket;
	public DatagramPacket packet;

	byte[] packetBuffer;

	private int checksum;
	private byte flags;
	private byte ackFlag;
	private byte[] dataBuffer;
	private boolean corrupted;

	public static final byte ACK_MASK = 0b00000001;
	public static final byte FIN_MASK = 0b00000010;

	public static final int PACKET_BUFFER_SIZE = 1000;
	public static final int CHECKSUM_OFFSET = 0;
	public static final int CHECKSUM_LENGTH = 4;
	public static final int DATA_BUFFER_OFFSET = 8;
	public static final int DATA_BUFFER_SIZE = 988;

	public static void main(String[] args) throws IOException, InterruptedException{

        // check if the number of command line argument is 4
		if (args.length != 4) {
			System.out.println("Usage: java FileSender <path/filename> "
				+ "<rcvHostName> <rcvPort> <rcvFileName>");
			System.exit(1);
		}

		new FileSender(args[0], args[1], args[2], args[3]);
	}

	public FileSender(String fileToOpen, String host, String port, String rcvFileName) throws IOException, InterruptedException {

        // Refer to Assignment 0 Ex #3 on how to open a file

        // UDP transmission is unreliable. Sender may overrun
        // receiver if sending too fast, giving packet lost as a result.
        // In that sense, sender may need to pause once in a while.
        // E.g. Thread.sleep(1); // pause for 1 millisecond

		InetAddress rcvAddress = InetAddress.getByName(host);
		int rcvPort = Integer.parseInt(port);

		packetBuffer = new byte[PACKET_BUFFER_SIZE];
		socket = new DatagramSocket();
		socket.setSoTimeout(10);

        // Sending FileName
		packet = new DatagramPacket(packetBuffer, PACKET_BUFFER_SIZE, rcvAddress, rcvPort);
		sendFileName(rcvFileName);

        // Sending Data
		FileInputStream fis = new FileInputStream(fileToOpen);
		BufferedInputStream bis = new BufferedInputStream(fis);

		dataBuffer = new byte[DATA_BUFFER_SIZE];
		int numBytes = bis.read(dataBuffer);
		boolean isEOF = false;
		while (numBytes != -1) {
			if (numBytes < DATA_BUFFER_SIZE) {
				isEOF = true;
			}
			packet.setLength(numBytes + DATA_BUFFER_OFFSET - 3); 
			System.out.println(numBytes);
			send(dataBuffer, isEOF);
			numBytes = bis.read(dataBuffer);
		}

		bis.close();
	}

	private void sendFileName(String rcvFileName) throws IOException {
		byte[] rcvFileNameByte = new byte[DATA_BUFFER_SIZE];
		ByteBuffer buffer = ByteBuffer.wrap(rcvFileNameByte);
		buffer.put(rcvFileName.getBytes());
		System.out.println("Sending File Name");
		send(buffer.array(), false);
	}

	private void send(byte[] data, boolean isEOF) throws IOException {
		buildPacket(data, isEOF);
		boolean waitForAck = true;
		byte[] ackBuffer = new byte[PACKET_BUFFER_SIZE];
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer, PACKET_BUFFER_SIZE);
		while (waitForAck) {
			System.out.println("Sending data");
			System.out.println(bytesToHex(packet.getData()));
			socket.send(packet);
			try {
				socket.receive(ackPacket);
				ByteBuffer buffer = ByteBuffer.wrap(ackBuffer);
				buffer.getInt();
				flags = buffer.get();
				if (isACK()) {
					System.out.println("ACK");
					waitForAck = false;
				}
			} catch (SocketTimeoutException e){
				waitForAck = true;
			}
		}
	}

	private void buildPacket(byte[] data, boolean isEOF) {
		ByteBuffer buffer = ByteBuffer.wrap(packetBuffer);
		buffer.putInt(0);
		if (isEOF){
			buffer.put((byte) 2);
		}
		else{
			buffer.put((byte) 0);
		}
		buffer.put(data);
		
		int checksum = FileReceiver.calculateChecksum(buffer.array());
		System.out.println(checksum);
		buffer.putInt(CHECKSUM_OFFSET, checksum);
	}

	private boolean isACK() {
		return (flags & ACK_MASK) == ACK_MASK;
	}
	
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}