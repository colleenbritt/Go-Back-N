import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class Client {

    private static final String DATA_FIELD = "0101010101010101";
    private static final String ACK_FIELD = "1010101010101010";

    String serverHostName;
    String fileName;
    int serverPort;
    int windowSize;
    int maximumSegmentSize;
    int ackNum = 0;
    int bufferNum = 0;
    int seq = 0; // current sequence
    File file;
    DatagramSocket dataSocket;
    InetAddress ipAddress;
    byte[][] fileBytes;

    Client(String serverHostName, int serverPort, String fileName, int N, int MSS) {
        this.serverHostName = serverHostName;
        this.serverPort = serverPort;
        this.file = new File(fileName);
        this.windowSize = N;
        this.maximumSegmentSize = MSS;
        this.fileName = fileName;
        try {
            this.ipAddress = InetAddress.getByName(serverHostName);
        } catch (UnknownHostException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        try {
            dataSocket = new DatagramSocket();
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) throws IOException{
        String hostName = args[0];
        int port = Integer.parseInt(args[1]);
        String fileName = args[2];
        int n = Integer.parseInt(args[3]);
        int mss = Integer.parseInt(args[4]);

        Client client = new Client(hostName, port, fileName, n, mss);

        long startTimer = System.currentTimeMillis();
		client.send();
		long endTimer = System.currentTimeMillis();
		System.out.println("Delay time in milliseconds: " + (endTimer - startTimer));
    }

    DatagramPacket rdt_send(int seq){
        // Get sequence number for header
        String seqNum = Integer.toBinaryString(seq);
	    for(int i = seqNum.length(); i < 32; i++)
            seqNum = "0" + seqNum;
        // End sequence

        // Get checksum for header
        String checksum = "";
        for (int i = 0; i < 8; i++){
            checksum += 0xff & fileBytes[seq][i];
        }
        while (checksum.length() < 16) {
            checksum += "0";
        }
        checksum = checksum.substring(0, 16);
        // End checksum
        
        String header = seqNum + checksum + DATA_FIELD;
        byte[] data = header.getBytes();
        byte[] filePacket = new byte[maximumSegmentSize + data.length];

        for(int i = 0; i < filePacket.length; i++) {
	        if(i < data.length)
               filePacket[i] = data[i];
	        else
               filePacket[i] = fileBytes[seq][i-data.length];
	    }
        return new DatagramPacket(filePacket, filePacket.length, ipAddress, serverPort);
    }
    
    public void send() {
        int lastSeq = (int) Math.ceil( (double) file.length() / maximumSegmentSize);

        System.out.println("File size is: " + file.length() + " bytes.");

        fileBytes = new byte[lastSeq + 1][maximumSegmentSize];
       
        /* Set up the input from file */
        try {
            byte [] bytearray  = new byte [(int)file.length()];
            FileInputStream fileInput = new FileInputStream(file);
            BufferedInputStream bufferInput = new BufferedInputStream(fileInput);
            bufferInput.read(bytearray, 0, bytearray.length);
            
            int index;
            int dataIndex;
            int sizeIndex;
            
            // Divide data by segment size
            for (dataIndex = 0; dataIndex < bytearray.length; dataIndex++) {
            	index = dataIndex / maximumSegmentSize;
            	sizeIndex = dataIndex % maximumSegmentSize;
            	fileBytes[index][sizeIndex] = bytearray[dataIndex];
            }
            
            bufferInput.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        while ((seq * maximumSegmentSize) < file.length()) {
            while(bufferNum < windowSize &&  seq < fileBytes.length ) {
                DatagramPacket dataPacket = rdt_send(seq);
                try {
                    // Send the packet
                    dataSocket.send(dataPacket);
                    seq++;
                    bufferNum++;
                    
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            byte[] ackBytes = new byte[1024];
            DatagramPacket ackPack = new DatagramPacket(ackBytes, ackBytes.length);
            boolean end = false;

            try {
                dataSocket.setSoTimeout(100);
                while (!end) {
                    // Receive the packet
                    dataSocket.receive(ackPack);
                    String ackString = new String(ackPack.getData());
                    
                    if (ackString.substring(48, 64).equals(ACK_FIELD)) {

                        // Get the sequence number
                        String seqNumString = ackString.substring(0, 32);
                        int seqNum = Integer.parseInt(seqNumString, 2);
                        
                        ackNum = seqNum;
                        if (ackNum == seq) {
                            end = true;
                            bufferNum = 0;
                            break;
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout, sequence number = " + ackNum);
                // Make sure we're at the right sequence number
                bufferNum = seq - ackNum; 
                seq = ackNum;
                // e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
