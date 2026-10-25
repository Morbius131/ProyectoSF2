package ser.cliente.infrastructure;

final class ServerResponses {

    private ServerResponses() {
    }

    static boolean isLockWaitTimeout(ServerClient.ServerResponse response) {
        return response != null
                && !response.success()
                && response.message() != null
                && response.message().toLowerCase().contains("lock wait timeout");
    }

    static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
