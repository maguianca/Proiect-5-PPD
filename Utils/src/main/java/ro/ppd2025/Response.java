package ro.ppd2025;

import java.io.Serializable;

public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    public boolean success;
    public String message;
    public long orderId;
    public double cost;

    public Response(boolean success, String message, long orderId, double cost) {
        this.success = success;
        this.message = message;
        this.orderId = orderId;
        this.cost = cost;
    }
}