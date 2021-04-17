import net.ddp2p.ASN1.ASN1DecoderFail;
import net.ddp2p.ASN1.Decoder;
import net.ddp2p.common.util.GetOpt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    static Logger logger = Logger.getLogger(Server.class.getName());

    private static void handleUDPPacket(DatagramPacket packet) {

        SocketAddress socketAddress = packet.getSocketAddress();
        logger.log(Level.INFO,String.format("New UDP packet from %s", socketAddress));

        byte[] inputData = packet.getData();

        byte[] replyBytes = handle(inputData);

        DatagramPacket sendPacket = new DatagramPacket(replyBytes, replyBytes.length, packet.getAddress(), packet.getPort());
        try {
            udpSocket.send(sendPacket);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        logger.log(Level.INFO, String.format("Done with UDP packet from %s", socketAddress));
    }



    private static void handleTCPConnection(Socket socket) {
        SocketAddress socketAddress = socket.getRemoteSocketAddress();
        logger.log(Level.INFO,String.format("New TCP Connection with %s", socketAddress));


        try {

            InputStream inputStream = socket.getInputStream();


            byte[] inputData = new byte[1024];
            int length = inputStream.read(inputData);
            byte[] replyBytes = handle(inputData);

            OutputStream outputStream = socket.getOutputStream();

            outputStream.write(replyBytes);
            outputStream.close();
            socket.close();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        logger.log(Level.INFO,String.format("Closed TCP Connection with %s", socketAddress));
    }

    private static Event constructEvent(Calendar calendar, String group, String description) {
        Event reply = new Event();
        reply.time = calendar;
        reply.group = group;
        reply.description = description;

        return reply;
    }

    private static byte[] handle(byte[] data) {

        Decoder decoder = new Decoder(data, 0, data.length);

        if (decoder.getTypeByte() == Request.TAG_CC2) {
            try {
                Request request = new Request().decode(decoder);

                System.out.printf("Requested group: %s\r\n", request.group);
            } catch (ASN1DecoderFail asn1DecoderFail) {
                asn1DecoderFail.printStackTrace();
            }
        }


        /*byte[] replyBytes = constructEvent(
                Calendar.getInstance(TimeZone.getTimeZone("UTC")),
                "Test",
                "Testing packet creation"
        ).encode();*/

        EventOK r = new EventOK();
        r.code = 0;

        return r.encode();

        //return replyBytes;
    }
}

