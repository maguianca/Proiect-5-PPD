package ro.ppd2025;
import java.io.Serializable;

public enum VehicleType implements Serializable {
    TIR_PRELATA(5.0, 20),      // Cost 5/km, 20 tone
    CAMION_FRIGORIFIC(7.0, 15), // Cost 7/km, 15 tone
    AUTOUTILITARA(2.0, 3);      // Cost 2/km, 3 tone

    public final double costPerKm;
    public final int maxCapacity;

    VehicleType(double cost, int capacity) {
        this.costPerKm = cost;
        this.maxCapacity = capacity;
    }
}