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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
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

        System.out.printf("New UDP packet from %s\r\n", socketAddress);

        byte[] inputData = packet.getData();

        byte[] replyBytes = handle(inputData, Mode.UDP, socketAddress);

        DatagramPacket sendPacket = new DatagramPacket(replyBytes, replyBytes.length, packet.getAddress(), packet.getPort());
        try {
            udpSocket.send(sendPacket);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        System.out.printf("Done with UDP packet from %s\r\n\r\n", socketAddress);
    }



    private static void handleTCPConnection(Socket socket) {
        SocketAddress socketAddress = socket.getRemoteSocketAddress();
        System.out.printf("New TCP Connection with %s\r\n", socketAddress);


        try {

            InputStream inputStream = socket.getInputStream();


            byte[] inputData = new byte[1024];
            int length = inputStream.read(inputData);
            byte[] replyBytes = handle(inputData, Mode.TCP, socketAddress);

            OutputStream outputStream = socket.getOutputStream();

            outputStream.write(replyBytes);
            outputStream.close();
            socket.close();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        System.out.printf("Closed TCP Connection with %s\r\n\r\n", socketAddress);
    }

    private static Event constructEvent(Calendar calendar, String group, String description) {
        Event reply = new Event();
        reply.time = calendar;
        reply.group = group;
        reply.description = description;

        return reply;
    }

    static ConcurrentHashMap<String, List<SocketAddress>> subscribersByGroup = new ConcurrentHashMap<>();

    private static byte[] handle(byte[] data, Mode mode, SocketAddress socketAddress) {

        Decoder decoder = new Decoder(data, 0, data.length);

        byte typeByte = decoder.getTypeByte();
        try {
            if (mode == Mode.UDP) {
                if (typeByte == Leave.TAG_CC4) {
                    Leave leave = new Leave().decode(decoder);

                    System.out.printf("\r\n%s requesting to leave group %s\r\n\r\n", socketAddress, leave.register.group);

                    leave(leave.register.group, socketAddress);
                    EventOK r = new EventOK();
                    r.code = 0;
                    return r.encode();
                } else if(typeByte == Register.TAG_CC3) {

                    Register register = new Register().decode(decoder);
                    System.out.printf("\r\n%s requesting to register for group %s\r\n\r\n", socketAddress, register.group);
                    subscribe(register.group, socketAddress);

                    EventOK r = new EventOK();
                    r.code = 0;
                    return r.encode();
                }
            }

            if (typeByte == Request.TAG_CC2) {

                    Request request = new Request().decode(decoder);

                    System.out.printf("Requested group: %s\r\n", request.group);

            } else if (typeByte == Event.TAG_CC1) {
                    Event event = new Event().decode(decoder);

                    System.out.printf("Event to be added: %s\r\n", event);

                    EventOK r = new EventOK();
                    r.code = 0;
                    return r.encode();

            }
        } catch (ASN1DecoderFail asn1DecoderFail) {
            asn1DecoderFail.printStackTrace();
        }

/*

        byte[] replyBytes = constructEvent(
                Calendar.getInstance(TimeZone.getTimeZone("UTC")),
                "Test",
                "Testing packet creation"
        ).encode();
*/

        // return replyBytes;
/*

        EventOK r = new EventOK();
        r.code = 1;
        return r.encode();
*/

        Register r = new Register();
        r.group = "CSE4232";

        Leave l = new Leave();
        l.register = r;
        return l.encode();
    }

    private static void subscribe(String group, SocketAddress socketAddress) {
        List<SocketAddress> addresses;
        if(!subscribersByGroup.containsKey(group)) {

            subscribersByGroup.put(group, Collections.synchronizedList(new ArrayList<>()));

        }
        addresses = subscribersByGroup.get(group);

        if (!addresses.contains(socketAddress)) {
            addresses.add(socketAddress);
        }
        System.out.printf("Subscribers for %s:\r\n", group);
        addresses.forEach(System.out::println);
    }

    private static void leave(String group, SocketAddress socketAddress) {
        if(subscribersByGroup.containsKey(group)) {
            List<SocketAddress> addresses = subscribersByGroup.get(group);

            addresses.removeIf(address -> address.equals(socketAddress));

            System.out.printf("Subscribers for %s:\r\n", group);
            addresses.forEach(System.out::println);
        }
    }
}

enum Mode {
    TCP, UDP;
}