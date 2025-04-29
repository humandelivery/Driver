package domain.model;

public class UpdateTaxiDriverStatusResponse {

    TaxiDriverStatus taxiDriverStatus;


    public TaxiDriverStatus getTaxiDriverStatus() {
        return taxiDriverStatus;
    }

    public void setTaxiDriverStatus(TaxiDriverStatus taxiDriverStatus) {
        this.taxiDriverStatus = taxiDriverStatus;
    }
}