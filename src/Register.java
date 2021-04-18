import net.ddp2p.ASN1.ASN1DecoderFail;
import net.ddp2p.ASN1.ASN1Type;
import net.ddp2p.ASN1.ASNObjArrayable;
import net.ddp2p.ASN1.Decoder;
import net.ddp2p.ASN1.Encoder;

// Register ::= [3] SEQUENCE {group UTF8String}
@ASN1Type(_class= Encoder.CLASS_CONTEXT, _pc=Encoder.PC_CONSTRUCTED, _tag=3)
public class Register extends ASNObjArrayable {

    final static byte TAG_CC3 = Encoder. buildASN1byteType(Encoder.CLASS_CONTEXT,
            Encoder.PC_CONSTRUCTED, (byte)3);

    String group;

    @Override
    public Encoder getEncoder() {
        Encoder e = new Encoder().initSequence();

        e.addToSequence(new Encoder(group));

        return e.setASN1TypeImplicit(TAG_CC3);
    }

    @Override
    public Register decode(Decoder dec) throws ASN1DecoderFail {
        Decoder d = dec.getContentImplicit();

        group = d.getFirstObject(true).getString();

        if (d.getTypeByte() != 0) throw new ASN1DecoderFail("Extra objects!");

        return this;
    }

    @Override
    public ASNObjArrayable instance() throws CloneNotSupportedException {
        return new Request();
    }
}
