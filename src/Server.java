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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static final int TEN_SECONDS = 10000;
    public static final int ONE_SECOND = 1000;
    public static final int ONE_MINUTE = 60000;
    static boolean running = true;
    static DatagramSocket udpSocket;
    static ServerSocket tcpSocket;

    public static void main(final String[] args) throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");

        char c;
        int argPort = 21998;

        final ExecutorService pool = Executors.newCachedThreadPool();

        while((c=GetOpt.getopt(args, "p:"))!=GetOpt.END){
            if (c == 'p') {
                argPort = Integer.parseInt(GetOpt.optarg);
            } else {
                System.out.println("Error: " + c);
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


        Timer subscriberWatcherTimer = new Timer();
        TimerTask subscriberWatcherTask = new TimerTask() {
            @Override
            public void run() {
                subscribersByGroup.forEach((group, subscribers) ->
                        subscribers.removeIf(subscriber ->
                                subscriber.expiration.isBefore(ZonedDateTime.now()
                                )
                        )
                );
            }
        };
        subscriberWatcherTimer.scheduleAtFixedRate(subscriberWatcherTask, 0, TEN_SECONDS);
        // Check every 10 seconds

        Timer eventWatcherTimer = new Timer();
        TimerTask eventWatcherTask = new TimerTask() {
            @Override
            public void run() {
                ArrayList<Event> events = popRecentEvents();

                events.forEach(event -> {
                    if (subscribersByGroup.containsKey(event.group)) {
                        subscribersByGroup.get(event.group).forEach(subscriber -> alert(subscriber, event));
                    }
                });
            }
        };
        eventWatcherTimer.scheduleAtFixedRate(eventWatcherTask, 0, ONE_SECOND);

    }

    private static void alert(Subscriber subscriber, Event event) {
        byte[] eventMessage = event.encode();
        DatagramPacket sendPacket = new DatagramPacket(eventMessage, eventMessage.length, subscriber.socketAddress);
        try {
            udpSocket.send(sendPacket);
            System.out.printf("Sent alert to %s for Event %s\r\n", subscriber.socketAddress, event);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

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

    /**
     * Keeps track of UDP alert connections
     */
    static ConcurrentHashMap<String, List<Subscriber>> subscribersByGroup = new ConcurrentHashMap<>();

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

                    System.out.printf("Requested group '%s' after %s\r\n", request.group, formatCalendar(request.after_time));

                    Answer answer = getEventsAfter(request.group, request.after_time);
                    System.out.println("Returning:");
                    Arrays.stream(answer.events).forEach(System.out::println);

                    return answer.encode();

            } else if (typeByte == Event.TAG_CC1) {
                    Event event = new Event().decode(decoder);

                    System.out.printf("Event to be added => %s\r\n", event);


                    addToDB(event.group, event.description, event.time);


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
        return replyBytes;

        Register r = new Register();
        r.group = "Test";
        return r.encode();

        Leave l = new Leave();
        l.register = r;
        return l.encode();


        Request r = new Request();
        r.group = "Test";
        r.after_time = Calendar.getInstance();
        r.after_time.setTimeInMillis(0);

        return r.encode();
*/

        EventOK r = new EventOK();
        r.code = 1;
        return r.encode();
    }

    private static void subscribe(String group, SocketAddress socketAddress) {
        List<Subscriber> addresses;
        if(!subscribersByGroup.containsKey(group)) {

            subscribersByGroup.put(group, Collections.synchronizedList(new ArrayList<>()));

        }
        addresses = subscribersByGroup.get(group);

        if (!addresses.contains(socketAddress)) {
            addresses.add(new Subscriber(socketAddress));
        }
        System.out.printf("Subscribers for %s:\r\n", group);
        addresses.forEach(System.out::println);
    }

    private static void leave(String group, SocketAddress socketAddress) {
        if(subscribersByGroup.containsKey(group)) {
            List<Subscriber> addresses = subscribersByGroup.get(group);

            addresses.removeIf(address -> address.equals(socketAddress));

            System.out.printf("Subscribers for %s:\r\n", group);
            addresses.forEach(System.out::println);
        }
    }

    protected static String formatCalendar(Calendar calendar) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH'h'mm'm'ss's'SSS'Z'", Locale.ENGLISH);

        return format.format(ZonedDateTime.ofInstant(calendar.toInstant(), ZoneId.of("UTC")));
    }


    protected static final String DB_CONNECTION_URL = "jdbc:sqlite:" + System.getenv("DB_PATH");
    private static void addToDB(String group, String description, Calendar time) {

        try (Connection conn = DriverManager.getConnection(DB_CONNECTION_URL)) {

            Statement statement = conn.createStatement();

            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `events`" +
                            "(id INTEGER PRIMARY KEY," +
                            "group_name TEXT," +
                            "description TEXT," +
                            "event_time INTEGER)"
            );

            PreparedStatement insertStatement = conn.prepareStatement(
                    "INSERT INTO events(group_name, description, event_time) VALUES (?, ?, ?)"
            );

            insertStatement.setString(1, group);
            insertStatement.setString(2, description);
            insertStatement.setLong(3, time.getTimeInMillis());

            insertStatement.execute();

        } catch (SQLException sqlException) {
            sqlException.printStackTrace();
        }
    }
    private static Answer getEventsAfter(String group, Calendar time) {
        try (Connection conn = DriverManager.getConnection(DB_CONNECTION_URL)) {

            Statement statement = conn.createStatement();
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `events`" +
                            "(id INTEGER PRIMARY KEY," +
                            "group_name TEXT," +
                            "description TEXT," +
                            "event_time INTEGER)"
            );
            ArrayList<Event> events = new ArrayList<>();

            PreparedStatement queryStatement = conn.prepareStatement(
                    "SELECT * FROM events WHERE event_time > ? AND group_name = ?"
            );

            queryStatement.setLong(1, time.getTimeInMillis());
            queryStatement.setString(2, group);

            getEventsFromStatement(events, queryStatement);

            Answer answer  = new Answer();
            answer.events = events.toArray(new Event[events.size()]);

            return answer;

        } catch (SQLException sqlException) {
            sqlException.printStackTrace();
        }
        return null;
    }

    private static ArrayList<Event> popRecentEvents() {
        try (Connection conn = DriverManager.getConnection(DB_CONNECTION_URL)) {
            long currentMillis = ZonedDateTime.now().toInstant().toEpochMilli();

            Statement statement = conn.createStatement();
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `events`" +
                            "(id INTEGER PRIMARY KEY," +
                            "group_name TEXT," +
                            "description TEXT," +
                            "event_time INTEGER)"
            );
            ArrayList<Event> events = new ArrayList<>();

            PreparedStatement queryStatement = conn.prepareStatement(
                    "SELECT * FROM events WHERE event_time <= ? AND event_time > ? ORDER BY event_time ASC"
            );

            queryStatement.setLong(1, currentMillis);
            queryStatement.setLong(2, currentMillis - ONE_MINUTE);

            // Do not return events over a minute old, silently delete them

            getEventsFromStatement(events, queryStatement);

            queryStatement = conn.prepareStatement(
                    "DELETE FROM events WHERE event_time <= ? "
            );

            queryStatement.setLong(1, currentMillis);
            queryStatement.execute();

            return events;

        }catch (SQLException sqlException) {
            sqlException.printStackTrace();
        }
        return null;
    }

    private static void getEventsFromStatement(ArrayList<Event> events, PreparedStatement queryStatement) throws SQLException {
        ResultSet rs = queryStatement.executeQuery();

        while (rs.next()) {
            Event e = new Event();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(rs.getLong("event_time"));

            e.group = rs.getString("group_name");
            e.description = rs.getString("description");
            e.time = c;

            events.add(e);
        }
    }

}

enum Mode {
    TCP, UDP;
}