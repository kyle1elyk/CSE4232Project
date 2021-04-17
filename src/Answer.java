import net.ddp2p.ASN1.ASN1DecoderFail;
import net.ddp2p.ASN1.ASN1Type;
import net.ddp2p.ASN1.ASNObjArrayable;
import net.ddp2p.ASN1.Decoder;
import net.ddp2p.ASN1.Encoder;

// EventOK ::= [0] SEQUENCE {code INTEGER}
@ASN1Type(_class= Encoder.CLASS_CONTEXT, _pc=Encoder.PC_CONSTRUCTED, _tag=3)
class Answer extends ASNObjArrayable {

    Event[] events;

    final static byte TAG_CC3 = Encoder. buildASN1byteType(Encoder.CLASS_CONTEXT,
            Encoder.PC_CONSTRUCTED, (byte)3);

    @Override
    public Encoder getEncoder() {

        Encoder e = new Encoder().initSequence();

        e.addToSequence(Encoder.getEncoder(events));

        return e.setASN1TypeImplicit(TAG_CC3);
    }

    @Override
    public Answer decode(Decoder dec) throws ASN1DecoderFail {

        Decoder d = dec.getContentImplicit();

        events = d.getFirstObject(true).getSequenceOf(Event.TAG_CC1, new Event[0], new Event());

        if (d.getTypeByte() != 0) throw new ASN1DecoderFail("Extra objects!");

        return this;
    }

    @Override
    public ASNObjArrayable instance() throws CloneNotSupportedException {
        return new Answer();
    }
}
