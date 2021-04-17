import net.ddp2p.ASN1.ASN1DecoderFail;
import net.ddp2p.ASN1.ASN1Type;
import net.ddp2p.ASN1.ASNObjArrayable;
import net.ddp2p.ASN1.Decoder;
import net.ddp2p.ASN1.Encoder;

import java.util.Calendar;

// Request ::= [2] SEQUENCE {group UTF8String, after_time GeneralizedTime}
@ASN1Type(_class= Encoder.CLASS_CONTEXT, _pc=Encoder.PC_CONSTRUCTED, _tag=2)
class Request extends ASNObjArrayable {

    String group;
    Calendar after_time;

    final static byte TAG_CC2 = Encoder. buildASN1byteType(Encoder.CLASS_CONTEXT,
            Encoder.PC_CONSTRUCTED, (byte)2);

    @Override
    public Encoder getEncoder() {

        Encoder e = new Encoder().initSequence();

        e.addToSequence(new Encoder(group));
        e.addToSequence(new Encoder(Encoder.getGeneralizedTime(after_time)));

        return e.setASN1TypeImplicit(TAG_CC2);
    }

    @Override
    public Request decode(Decoder dec) throws ASN1DecoderFail {

        Decoder d = dec.getContentImplicit();

        group = d.getFirstObject(true).getString();
        after_time = d.getFirstObject(true).getGeneralizedTimeCalenderAnyType();

        if (d.getTypeByte() != 0) throw new ASN1DecoderFail("Extra objects!");

        return this;
    }

    @Override
    public ASNObjArrayable instance() throws CloneNotSupportedException {
        return new Request();
    }
}
