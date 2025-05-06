package domain.model;
import java.time.LocalDateTime;


public class DrivingSummaryResponse {
    private Long callId;
    private String customerLoginId;
    private String taxiDriverLoginId;
    private Location origin;
    private LocalDateTime pickupTime;
    private Location destination;
    private LocalDateTime arrivingTime;
    private DrivingStatus drivingStatus;
    private boolean reported;

    public Long getCallId() {
        return callId;
    }

    public void setCallId(Long callId) {
        this.callId = callId;
    }

    public String getCustomerLoginId() {
        return customerLoginId;
    }

    public void setCustomerLoginId(String customerLoginId) {
        this.customerLoginId = customerLoginId;
    }

    public String getTaxiDriverLoginId() {
        return taxiDriverLoginId;
    }

    public void setTaxiDriverLoginId(String taxiDriverLoginId) {
        this.taxiDriverLoginId = taxiDriverLoginId;
    }

    public Location getOrigin() {
        return origin;
    }

    public void setOrigin(Location origin) {
        this.origin = origin;
    }

    public LocalDateTime getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(LocalDateTime pickupTime) {
        this.pickupTime = pickupTime;
    }

    public Location getDestination() {
        return destination;
    }

    public void setDestination(Location destination) {
        this.destination = destination;
    }

    public LocalDateTime getArrivingTime() {
        return arrivingTime;
    }

    public void setArrivingTime(LocalDateTime arrivingTime) {
        this.arrivingTime = arrivingTime;
    }

    public DrivingStatus getDrivingStatus() {
        return drivingStatus;
    }

    public void setDrivingStatus(DrivingStatus drivingStatus) {
        this.drivingStatus = drivingStatus;
    }

    public boolean isReported() {
        return reported;
    }

    public void setReported(boolean reported) {
        this.reported = reported;
    }
}
