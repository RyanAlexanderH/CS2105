import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

class FileReceiver {

	public DatagramSocket socket;
	public DatagramPacket packet;

	private byte[] rcvBuffer;

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


	public static void main(String[] args) throws IOException{

        // check if the number of command line argument is 1
		if (args.length != 1) {
			System.out.println("Usage: java FileReceiver port");
			System.exit(1);
		}

		new FileReceiver(args[0]);
	}

	public FileReceiver(String localPort) throws IOException{
		int port = Integer.parseInt(localPort);
		socket = new DatagramSocket(port);

		rcvBuffer = new byte[PACKET_BUFFER_SIZE];
		packet = new DatagramPacket(rcvBuffer, PACKET_BUFFER_SIZE);

        // Receiving FileName
		receivePacket();

        // Preparing File
		String rcvFileName = new String(dataBuffer);
		FileOutputStream fos = new FileOutputStream(rcvFileName.trim());
		BufferedOutputStream bos = new BufferedOutputStream(fos);

        // Receiving Data
		while (!isEOF()) {
			receivePacket();
			bos.write(dataBuffer);
		}

		bos.close();
	}

	private void receivePacket() throws IOException {
		this.corrupted = true;
		while (this.corrupted) {
			System.out.println("Receiving packet");
			System.out.println(bytesToHex(packet.getData()));
			socket.receive(packet);
			processPacket();			
		}
	}

	private void processPacket() throws IOException{
		rcvBuffer = packet.getData();
		ByteBuffer buffer = ByteBuffer.wrap(rcvBuffer, 0, packet.getLength());
		
		checksum = buffer.getInt();
		flags = buffer.get();
		dataBuffer = new byte[buffer.remaining()];
		buffer.get(dataBuffer);
		dataBuffer = ByteBuffer.wrap(dataBuffer).array();
		integrityCheck(rcvBuffer);
	}

	private void integrityCheck(byte[] b) throws IOException {
		Arrays.fill(b, CHECKSUM_OFFSET, CHECKSUM_OFFSET + CHECKSUM_LENGTH, (byte) 0);
		if (checksum == calculateChecksum(b)) {
			corrupted = false;
			System.out.println("Data is good");
		}
		else {
			corrupted = true;
			System.out.println("Data is corrupted");
		}
		sendResponse();
	}

	public static int calculateChecksum(byte[] b) {
		CRC32 crc = new CRC32();
		crc.update(b);
		return (int)crc.getValue();
	}

	private void sendResponse() throws IOException {
		System.out.println("Sending response");
		byte[] ackBuffer = new byte[PACKET_BUFFER_SIZE];
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer, PACKET_BUFFER_SIZE, packet.getSocketAddress());

		ByteBuffer buffer = ByteBuffer.wrap(ackBuffer);
		buffer.putInt(0);
		if (corrupted == false) {
			buffer.put((byte) 1);
		}
		socket.send(ackPacket);
	}

	private boolean isEOF() {
		return (flags & FIN_MASK) == FIN_MASK;
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