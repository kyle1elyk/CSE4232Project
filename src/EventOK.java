import net.ddp2p.ASN1.ASN1DecoderFail;
import net.ddp2p.ASN1.ASN1Type;
import net.ddp2p.ASN1.ASNObjArrayable;
import net.ddp2p.ASN1.Decoder;
import net.ddp2p.ASN1.Encoder;

// EventOK ::= [0] SEQUENCE {code INTEGER}
@ASN1Type(_class= Encoder.CLASS_CONTEXT, _pc=Encoder.PC_CONSTRUCTED, _tag=0)
class EventOK extends ASNObjArrayable {

    int code;

    final static byte TAG_CC0 = Encoder. buildASN1byteType(Encoder.CLASS_CONTEXT,
            Encoder.PC_CONSTRUCTED, (byte)0);

    @Override
    public Encoder getEncoder() {

        Encoder e = new Encoder().initSequence();

        e.addToSequence(new Encoder(code));

        return e.setASN1TypeImplicit(TAG_CC0);
    }

    @Override
    public EventOK decode(Decoder dec) throws ASN1DecoderFail {

        Decoder d = dec.getContentImplicit();

        code = d.getFirstObject(true).getIntValue();

        if (d.getTypeByte() != 0) throw new ASN1DecoderFail("Extra objects!");

        return this;
    }

    @Override
    public ASNObjArrayable instance() throws CloneNotSupportedException {
        return new EventOK();
    }
}
