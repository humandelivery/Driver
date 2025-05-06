package domain.model;

public class DrivingInfoResponse {
    private boolean isDrivingStarted;
    private boolean isDrivingFinished;

    public boolean isDrivingStarted() {
        return isDrivingStarted;
    }

    public void setDrivingStarted(boolean drivingStarted) {
        isDrivingStarted = drivingStarted;
    }

    public boolean isDrivingFinished() {
        return isDrivingFinished;
    }

    public void setDrivingFinished(boolean drivingFinished) {
        isDrivingFinished = drivingFinished;
    }
}
