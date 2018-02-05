import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

class Client {
    static final int PACKET_SIZE = 1044; // 1024 + 20 byte header
    static final int PAYLOAD_SIZE = 1024;

    public static int packetHashCode(ByteBuffer packet) {
        int hashcode = 0;
        while (packet.hasRemaining()) {
            hashcode += packet.get();
        }
        packet.flip();
        return hashcode;
    }

    public static void main(String args[]) {

        if (args.length < 2) {
            System.out.println("Must supply a port number and IP address.");
            System.exit(1);
        }

        Console cons = System.console();
        int portNum = Integer.parseInt(args[0]);
        String ipAddr = args[1];

        try {
            DatagramChannel dg = DatagramChannel.open();
            ByteBuffer packet = ByteBuffer.allocate(PACKET_SIZE);
            long packetNum;
            long numPackets = 0;
            int hashcode;

            // get filename to transfer and send it to server
            String filename = cons.readLine("Enter a filename: ");
            ByteBuffer filenameBuf = ByteBuffer.wrap(filename.getBytes());
            dg.send(filenameBuf, new InetSocketAddress(ipAddr, portNum));

            // create output file
            String fileExtension = filename.substring(filename.indexOf("."));
            FileOutputStream outFile = new FileOutputStream("outFile" + fileExtension, false);
            
            ArrayList<Integer> noDups = new ArrayList();

            ByteBuffer ack = ByteBuffer.allocate(12);

            // recieve rest of packets in loop
            boolean sendingInProcess = true;
            while (sendingInProcess) {
                packet = ByteBuffer.allocate(PACKET_SIZE);

                // recieve next packet and get it's number
                dg.receive(packet);
                packet.flip();
                hashcode = packet.getInt();
                packet.rewind();
                packet.putInt(0);
                packet.rewind();

                // If the packet is not corrupted
                if (hashcode == Client.packetHashCode(packet)) {
                    packetNum = packet.getLong(4);

                    // if we're on the first iteration of the loop
                    if (numPackets == 0) {
                        numPackets = packet.getLong(12);
                        System.out.println("Total number of packets: " + numPackets);
                        for (int i = 0; i < numPackets; i++) {
                            noDups.add(0);
                        }
                    }

                    ack.rewind();
                    ack.putLong(packetNum);
                    ack.putInt(0);
                    ack.rewind();
                    int ackHashcode = Client.packetHashCode(ack);
                    ack.position(8);
                    ack.putInt(ackHashcode);
                    ack.rewind();
                    dg.send(ack, new InetSocketAddress(ipAddr, portNum));

                    if (noDups.get((int)packetNum) == 0) {
                        // write next payload
                        System.out.println("Writing payload " + (packetNum+1) + " of " + numPackets);
                        packet.position(20);
                        outFile.getChannel().write(packet, (1024 * packetNum));
                        noDups.set((int)packetNum, 1);
                    }

                    if (noDups.indexOf(0) == -1) {
                        sendingInProcess = false;
                    }
                }
            }

            System.out.println("File transfer finished");
            outFile.close();
            dg.close();
        } catch (IOException e) {
            System.out.println("error");
        }
    }
}
