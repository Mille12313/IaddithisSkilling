package bloody.devmules.iaddithisSkilling;

public record SessionData(String fishingCaptcha) {

    public boolean hasFishingCaptcha() {
        return fishingCaptcha != null;
    }

}
