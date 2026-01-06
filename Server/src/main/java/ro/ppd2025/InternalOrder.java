package ro.ppd2025;

public class InternalOrder {
    long id;
    int clientId;
    int depotId;
    VehicleType vehicleType;
    int weight;
    double value;
    long reservationTime;
    boolean confirmed;
    String cnp;

    public InternalOrder(long id, int clientId, int depotId, VehicleType vehicleType, int weight, double value,String cnp) {
        this.id = id;
        this.clientId = clientId;
        this.depotId = depotId;
        this.vehicleType = vehicleType;
        this.weight = weight;
        this.value = value;
        this.reservationTime = System.currentTimeMillis();
        this.confirmed = false;
        this.cnp = cnp;
    }
}