package domain.model;


public class CallMessageResponse {
    private Long callId;
    private Location expectedOrigin;
    private Location expectedDestination;

    public Long getCallId() {
        return callId;
    }

    public void setCallId(Long callId) {
        this.callId = callId;
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
