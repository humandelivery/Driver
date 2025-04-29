package domain.model;

public class UpdateLocationRequest {

    private String customerLoginId;
    private Location location;

    public String getCustomerLoginId() {
        return customerLoginId;
    }
    public UpdateLocationRequest() {
    }
    public void setCustomerLoginId(String customerLoginId) {
        this.customerLoginId = customerLoginId;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
