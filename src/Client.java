import net.ddp2p.ASN1.ASN1DecoderFail;
import net.ddp2p.ASN1.Decoder;
import net.ddp2p.common.util.GetOpt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.Scanner;

public class Client {
    public static void main(final String[] args) {

        char c;
        String host = "localhost", command = null;
        int argPort = 21998;
        Mode mode = Mode.TCP;


        while((c= GetOpt.getopt(args, "p:tuh:c:"))!=GetOpt.END){
            if (c == 'p') {
                argPort = Integer.parseInt(GetOpt.optarg);
            } else if (c == 't') {
                mode = Mode.TCP;
            } else if (c == 'u') {
                mode = Mode.UDP;
            } else if (c == 'h') {
                host = GetOpt.optarg;
            } else if (c == 'c') {
                command = GetOpt.optarg;
            }
        }
        System.out.println("Got here");
        if (command == null ) {
            System.err.println("Add a command with -c");
            System.exit(1);
        }

        String[] commandArgs = command.split(";");

        if (mode == Mode.TCP) {
            try(Socket socket = new Socket(host, argPort)) {
                String group, description;
                Calendar calendar;
                OutputStream outputStream = socket.getOutputStream();

                InputStream inputStream = socket.getInputStream();

                switch (commandArgs[0]) {
                    case "EVENT_DEFINITION":
                            calendar = stringToCalendar(commandArgs[1]);
                            description = commandArgs[2];
                            group = commandArgs[3];

                            Event event = new Event();
                            event.time = calendar;
                            event.group = group;
                            event.description = description;


                            byte[] replyBytes = event.encode();



                            outputStream.write(replyBytes);


                            byte[] inputData = new byte[1024];
                            int length = inputStream.read(inputData);
                            outputStream.close();

                            Decoder eventOKDecoder = new Decoder(inputData, 0, length);

                            if (eventOKDecoder.getTypeByte() == EventOK.TAG_CC0) {
                                EventOK response = new EventOK().decode(eventOKDecoder);
                                System.out.printf("Server returned %s", response.code == 0? "EVENT OK": "EVENT NOT OK");
                            }

                        break;
                    case "GET_NEXT_EVENTS":
                        group = commandArgs[1];
                        calendar = stringToCalendar(commandArgs[2]);

                        Request request = new Request();
                        request.group = group;
                        request.after_time = calendar;

                        byte[] requestBytes = request.encode();
                        outputStream.write(requestBytes);

                        byte[] requestReply = new byte[1024];
                        int requestReplyLength = inputStream.read(requestReply);
                        outputStream.close();

                        Decoder requestDecoder = new Decoder(requestReply, 0, requestReplyLength);

                        if (requestDecoder.getTypeByte() == Answer.TAG_CC3) {
                            Answer answer = new Answer().decode(requestDecoder);
                            System.out.println("Server returned:");
                            Arrays.stream(answer.events).forEach(System.out::println);
                        }

                        break;
                }
            } catch (IOException ioException) {

            } catch (ASN1DecoderFail asn1DecoderFail) {
                asn1DecoderFail.printStackTrace();
            }

        } else {
            try (DatagramSocket socket = new DatagramSocket()) {
                System.out.printf("Connecting from %d\r\nEOL to exit...", socket.getLocalPort());

                if (commandArgs[0].equals("REGISTER")) {
                    Register register = new Register();
                    register.group = commandArgs[1];

                    byte[] registerMsg = register.encode();
                    DatagramPacket packet = new DatagramPacket(registerMsg, registerMsg.length, InetAddress.getByName(host), argPort);
                    socket.send(packet);

                }else if (commandArgs[0].equals("LEAVE")) {
                    Leave leave = new Leave();
                    leave.register = new Register();
                    leave.register.group = commandArgs[1];

                    byte[] leaveMsg = leave.encode();
                    DatagramPacket packet = new DatagramPacket(leaveMsg, leaveMsg.length, InetAddress.getByName(host), argPort);
                    socket.send(packet);

                }

                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                Scanner in = new Scanner(System.in);

                socket.setSoTimeout(1000);
                while (in.hasNextLine()) {
                    try {
                        socket.receive(packet);
                        byte[] input = packet.getData();
                        Decoder decoder = new Decoder(input, 0, input.length);

                        if (decoder.getTypeByte() == EventOK.TAG_CC0) {
                            EventOK eventOK = new EventOK().decode(decoder);
                            System.out.printf("Server returned %s\r\n", eventOK.code == 0? "OK": "NOT OK");
                        } else if (decoder.getTypeByte() == Event.TAG_CC1){
                            Event event = new Event().decode(decoder);
                            System.out.printf("Server returned event: %s\r\n", event);
                        }

                    } catch (SocketTimeoutException socketTimeoutException) {

                    } catch (ASN1DecoderFail asn1DecoderFail) {
                        asn1DecoderFail.printStackTrace();
                    }
                }

                in.close();
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    private static Calendar stringToCalendar(String time) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd:HH'h'mm'm'ss's'SSS'Z'", Locale.ENGLISH);
        try {
            calendar.setTime(sdf.parse(time));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return calendar;
    }
}
