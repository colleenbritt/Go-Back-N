
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Random;


public class Server {

private static final String DATA_FIELD = "0101010101010101";
private static final String ACK_FIELD = "1010101010101010";
private static final String CHECKSUM_FIELD = "0000000000000000";

    int portNumber;
    int ack;
    String fileName;
    DatagramSocket dataSocket;
    boolean end = false;
    InetAddress ipAddress;

    Server(int portNumber, String fileName) {
        this.portNumber = portNumber;
        this.fileName = fileName;
        ack = 0;
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        String file = args[1];
        float probability = Float.parseFloat(args[2]);
        Server server = new Server(port, file);
        System.out.println("Waiting for file from client.");
        try {
            server.dataSocket = new DatagramSocket(port);
            
            FileOutputStream fileOutput = new FileOutputStream(file);
            BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutput);
            byte[] receivedData;
            Random r = new Random();
            float randomNum;
            
            while (!server.end) {
                receivedData = new byte[2048];
                DatagramPacket packet = new DatagramPacket(receivedData, receivedData.length);
                
                server.dataSocket.receive(packet);

                byte[] bytes = new byte[packet.getLength() - 64];
                System.arraycopy(receivedData, 64, bytes, 0, bytes.length);
                byte[] packetData = packet.getData();
                String seq = new String(Arrays.copyOfRange(packetData, 0, 32));
                int seqNum = Integer.parseInt(seq, 2); // Sequence number
                String check = new String(Arrays.copyOfRange(packetData, 32, 48)); 
                String dataField = new String(Arrays.copyOfRange(packetData, 48, 64));
                byte[] data = Arrays.copyOfRange(packetData, 64, packetData.length);
                
                randomNum = r.nextFloat();
                if (randomNum <= probability) {
                    System.out.println("Packet loss, sequence number = " + seqNum);
                    continue;
                }

                // Get checksum for header
                String checksum = "";
                for (int i = 0; i < 8; i++){
                    checksum += 0xff & data[i];
                }
                while (checksum.length() < 16) {
                    checksum += "0";
                }
                checksum = checksum.substring(0, 16);
                // End checksum

                server.ipAddress = packet.getAddress();
                server.portNumber = packet.getPort();
                if (checksum.equals(check)) {
                    if (server.ack == seqNum) {
                        server.ack++;
                        if (dataField.equals(DATA_FIELD)) {
                            fileOutput.write(bytes);
    
                            // Get sequence number
                            String seqNumber = Integer.toBinaryString(server.ack);
                            for(int i = seqNumber.length(); i < 32; i++)
                                seqNumber = "0" + seqNumber;
                            // End sequence

                            String header = seqNumber + CHECKSUM_FIELD + ACK_FIELD;
                            byte[] ackBytes = header.getBytes();
                            DatagramPacket dataPacket = new DatagramPacket(ackBytes, ackBytes.length, server.ipAddress, server.portNumber);
                            server.dataSocket.send(dataPacket);
                        } else {
                            server.end = true;
                        }   
                    }
                }
            }
            bufferedOutput.flush();
            bufferedOutput.close();

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
