package eu.mikroskeem.moarsms;

import java.util.Map;

public interface Platform {
    /**
     * Shut down service handler
     * Currently used only in CLI version
     */
    void shutdown();

    /**
     * Get defined services and their secrets
     *
     * @return Map of service and it's secret
     */
    Map<String,String> getServiceSecrets();

    /**
     * Invoke service
     *
     * @param serviceId Service ID
     * @param message message in SMS (after keyword)
     * @return Reply to send to buyer
     */
    String invokeService(String serviceId, String message);

    /**
     * Test switch
     *
     * @return Whether test messages are allowed or not
     */
    boolean allowTest();

    /**
     * Get response messages
     *
     * @return Response message for service
     */
    String getMessage(String path);
}
