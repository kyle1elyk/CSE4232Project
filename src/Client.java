import net.ddp2p.ASN1.ASN1DecoderFail;
import net.ddp2p.ASN1.Decoder;
import net.ddp2p.common.util.GetOpt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

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
