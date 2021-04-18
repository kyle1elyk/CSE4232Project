import java.net.SocketAddress;
import java.time.ZonedDateTime;

public class Subscriber {
    SocketAddress socketAddress;
    ZonedDateTime expiration;

    public Subscriber(SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
        this.expiration = ZonedDateTime.now().plusHours(1);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SocketAddress) return socketAddress.equals(obj);
        if (obj instanceof Subscriber) {
            Subscriber that = (Subscriber) obj;
            if (that.socketAddress.equals(this.socketAddress)) {

                if (that.expiration.isAfter(this.expiration)) {
                    this.expiration = that.expiration;
                } else {
                    that.expiration = this.expiration;
                }

                return true;
            }

            return false;
        }


        return super.equals(obj);
    }

    @Override
    public String toString() {
        return String.format("%s (expires: %s)", socketAddress, expiration);
    }
}
