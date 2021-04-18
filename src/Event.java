import net.ddp2p.ASN1.ASN1DecoderFail;
import net.ddp2p.ASN1.ASN1Type;
import net.ddp2p.ASN1.ASNObjArrayable;
import net.ddp2p.ASN1.Decoder;
import net.ddp2p.ASN1.Encoder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;

// Event ::= [1] SEQUENCE {time GeneralizedTime, group UTF8String, description UTF8String}
@ASN1Type(_class= Encoder.CLASS_CONTEXT, _pc=Encoder.PC_CONSTRUCTED, _tag=1)
class Event extends ASNObjArrayable {

    Calendar time;
    String group;
    String description;

    final static byte TAG_CC1 = Encoder. buildASN1byteType(Encoder.CLASS_CONTEXT,
            Encoder.PC_CONSTRUCTED, (byte)1);

    @Override
    public Encoder getEncoder() {

        Encoder e = new Encoder().initSequence();

        e.addToSequence(new Encoder(Encoder.getGeneralizedTime(time)).setASN1Type(Encoder.TAG_GeneralizedTime));
        e.addToSequence(new Encoder(group));
        e.addToSequence(new Encoder(description));

        return e.setASN1TypeImplicit(TAG_CC1);
    }

    @Override
    public Event decode(Decoder dec) throws ASN1DecoderFail {

        Decoder d = dec.getContentImplicit();

        time = d.getFirstObject(true).getGeneralizedTimeCalenderAnyType();
        group = d.getFirstObject(true).getString();
        description = d.getFirstObject(true).getString();

        if (d.getTypeByte() != 0) throw new ASN1DecoderFail("Extra objects!");

        return this;
    }

    @Override
    public ASNObjArrayable instance() throws CloneNotSupportedException {
        return new Event();
    }

    @Override
    public String toString() {

        return "Event{" +
                "time=" + Server.formatCalendar(time) +
                ", group='" + group + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
