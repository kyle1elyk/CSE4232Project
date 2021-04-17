import net.ddp2p.common.util.GetOpt;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    static boolean running = true;
    static DatagramSocket udpSocket;
    static ServerSocket tcpSocket;

    public static void main(final String[] args) {
        char c;
        int argPort = 21998;

        final ExecutorService pool = Executors.newCachedThreadPool();

        while((c=GetOpt.getopt(args, "p:"))!=GetOpt.END){
            switch(c){
                case 'p': argPort = Integer.parseInt(GetOpt.optarg); break;
                default:
                    System.out.println("Error: "+c);
                    return;
            }
        }

        final int port = argPort;

        /*
        Event e1 = new Event();
        e1.time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        e1.group = "CSE 4232";
        e1.description = "Network Programming";

        Event e2 = new Event();
        e2.time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        e2.group = "COM 1102";
        e2.description = "Literature";


        Event[] events = new Event[]{e1, e2};
        Answer a1 = new Answer();
        a1.events = events;

        byte[] a1Encoded = a1.encode();

        Decoder decoder = new Decoder(a1Encoded, 0, a1Encoded.length);
        try {
            Answer a2 = new Answer().decode(decoder);
            System.out.println(a2.events.length);
            Arrays.stream(a2.events).forEach(System.out::println);

        } catch (ASN1DecoderFail asn1DecoderFail) {
            asn1DecoderFail.printStackTrace();
        }
        */


        pool.execute(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                tcpSocket = serverSocket;
                while (running) {
                    Socket socket = serverSocket.accept();

                    pool.execute(() -> handleTCPConnection(socket));
                }

            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        });

        pool.execute(() -> {
            try(DatagramSocket socket = new DatagramSocket(port)){
                udpSocket = socket;
                while (running) {
                    byte[] buf = new byte[512];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);

                    socket.receive(packet);
                    pool.execute(() -> handleUDPPacket(packet));
                }

            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        });

    }

    private static void handleUDPPacket(DatagramPacket packet) {
        // TODO: Replace Hello World UDP response
        String reply = "Hello World!";
        DatagramPacket sendPacket = new DatagramPacket(reply.getBytes(), reply.length(), packet.getAddress(), packet.getPort());
        try {
            udpSocket.send(sendPacket);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

    }

    private static void handleTCPConnection(Socket socket) {
        // TODO: Replace Hello World TCP response
        try {
            OutputStream outputStream = socket.getOutputStream();

            outputStream.write("Hello world!\r\n".getBytes());
            outputStream.close();
            socket.close();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

    }
}

