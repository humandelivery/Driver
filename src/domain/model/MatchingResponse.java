package domain.model;

public class MatchingResponse {

    private String customerName;
    private String customerLoginId;
    private String customerPhoneNumber;
    private Location expectedOrigin;
    private Location expectedDestination;

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerLoginId() {
        return customerLoginId;
    }

    public void setCustomerLoginId(String customerLoginId) {
        this.customerLoginId = customerLoginId;
    }

    public String getCustomerPhoneNumber() {
        return customerPhoneNumber;
    }

    public void setCustomerPhoneNumber(String customerPhoneNumber) {
        this.customerPhoneNumber = customerPhoneNumber;
    }

    public Location getExpectedOrigin() {
        return expectedOrigin;
    }

    public void setExpectedOrigin(Location expectedOrigin) {
        this.expectedOrigin = expectedOrigin;
    }

    public Location getExpectedDestination() {
        return expectedDestination;
    }

    public void setExpectedDestination(Location expectedDestination) {
        this.expectedDestination = expectedDestination;
    }
}
