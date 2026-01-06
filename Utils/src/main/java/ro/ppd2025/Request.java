package ro.ppd2025;

import java.io.Serializable;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type { RESERVE, CONFIRM }

    public Type type;
    public int clientId;
    public int depotId;     // Pt Rezervare
    public VehicleType vehicleType; // Pt Rezervare
    public int weight;      // Pt Rezervare
    public long orderId;    // Pt Confirmare
    public String cnp;

    // Constructor Rezervare
    public Request(int clientId, int depotId, VehicleType vehicleType, int weight,String cnp) {
        this.type = Type.RESERVE;
        this.clientId = clientId;
        this.depotId = depotId;
        this.vehicleType = vehicleType;
        this.weight = weight;
        this.cnp = cnp;
    }

    // Constructor Confirmare
    public Request(long orderId,String cnp) {
        this.type = Type.CONFIRM;
        this.orderId = orderId;
        this.cnp=cnp;
    }
}