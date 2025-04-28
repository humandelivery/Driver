package domain.model;

public class UpdateTaxiDriverStatusResponse {

    RequestStatus requestStatus;
    TaxiDriverStatus taxiDriverStatus;

    public RequestStatus getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(RequestStatus requestStatus) {
        this.requestStatus = requestStatus;
    }

    public TaxiDriverStatus getTaxiDriverStatus() {
        return taxiDriverStatus;
    }

    public void setTaxiDriverStatus(TaxiDriverStatus taxiDriverStatus) {
        this.taxiDriverStatus = taxiDriverStatus;
    }
}