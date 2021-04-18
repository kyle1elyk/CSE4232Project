import net.ddp2p.ASN1.ASN1DecoderFail;
import net.ddp2p.ASN1.ASN1Type;
import net.ddp2p.ASN1.ASNObj;
import net.ddp2p.ASN1.Decoder;
import net.ddp2p.ASN1.Encoder;

// Leave ::= [4] Register
@ASN1Type(_class= Encoder.CLASS_CONTEXT, _pc=Encoder.PC_CONSTRUCTED, _tag=3)
public class Leave extends ASNObj {

    final static byte TAG_CC4 = Encoder. buildASN1byteType(Encoder.CLASS_CONTEXT,
            Encoder.PC_CONSTRUCTED, (byte)4);

    Register register;

    @Override
    public Encoder getEncoder() {
        Encoder e = new Encoder().initSequence();

        e.addToSequence(register.getEncoder());

        return e.setASN1TypeImplicit(TAG_CC4);
    }

    @Override
    public Leave decode(Decoder dec) throws ASN1DecoderFail {
        Decoder d = dec.getContentImplicit();

        register = new Register().decode(d.getFirstObject(true));

        if (d.getTypeByte() != 0) throw new ASN1DecoderFail("Extra objects!");

        return this;
    }

    @Override
    public ASNObj instance() throws CloneNotSupportedException {
        return new Leave();
    }
}
