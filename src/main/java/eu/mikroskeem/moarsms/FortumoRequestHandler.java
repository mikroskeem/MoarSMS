package eu.mikroskeem.moarsms;

import fi.iki.elonen.NanoHTTPD;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
final class FortumoRequestHandler extends NanoHTTPD {
    private final Map<String, String> serviceSecrets;
    private final boolean allowTest;

    FortumoRequestHandler(String host, int port){
        super(host, port);
        serviceSecrets = API.getInstance().getServiceSecrets();
        allowTest = API.getInstance().allowTest();
    }

    @Override public Response serve(IHTTPSession session) {
        if(!session.getUri().equals("/")){
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }
        log.info("Beep-boop, got request");
        Map<String, String> params = session.getParms();

        String originIP = session.getHeaders().get("x-real-ip");
        if(originIP != null){
            String nginxProxy = session.getHeaders().get("x-nginx-proxy");
            if (nginxProxy == null || !nginxProxy.equals("true")) {
                log.info("X-Forwaded-For was present, but X-Nginx-Proxy not, bailing out!");
                return sendFailResponse(API.getInstance().getMessage("badconfig.reverseProxy"));
            }
        } else {
            originIP = session.getHeaders().get("remote-addr");
        }

        /* Get basic headers */
        String useragent = session.getHeaders().get("user-agent");

        /* Get Fortumo headers */
        String sender = params.get("sender");
        String keyword = params.get("keyword");
        String message = params.get("message");
        String serviceId = params.get("service_id");
        String signature = params.get("sig");
        String test = params.get("test");

        /* Log them */
        log.info("Origin IP: {}", originIP);
        log.info("Useragent: {}", useragent);
        log.info("Sender: {}", sender);
        log.info("Keyword: {}", keyword);
        log.info("Message: {}", message);
        log.info("Test: {}", test);

        /* Check parameters */
        if(notNull(signature) && notNull(serviceId) && notNull(keyword) && notNull(message)){
            log.info("Got valid Fortumo request!");
            /* Check for IP */
            if(!FortumoUtils.checkIP(originIP)){
                log.info("Request was from non-whitelisted IP '{}'!", originIP);
                return sendFailResponse(API.getInstance().getMessage("validation.forbiddenIP"));
            }
            /* Check for service id */
            String checkSignature = serviceSecrets.get(serviceId);
            if(checkSignature == null){
                log.info("Service '{}' was requested, but it is not defined!", serviceId);
                log.info("Keyword was '{}', maybe this helps", keyword);
                return sendFailResponse(API.getInstance().getMessage("validation.undefinedService"));
            }

            /* Check for signature */
            if(!FortumoUtils.checkSignature(params, checkSignature)){
                log.info("Signature seems incorrect, correct is '{}', but '{}' was provided",
                        checkSignature, signature);
                return sendFailResponse(API.getInstance().getMessage("validation.signatureIncorrect"));
            }

            /* Check if message it's test message and if they're allowed */
            if(test.equals("true") && !allowTest){
                log.info("Test messages are disabled from config, bailing out");
                return sendResponse(API.getInstance().getMessage("test.notallowed"));
            }

            log.info("Message is valid");
            return sendResponse(API.getInstance().invokeService(serviceId, message));
        }

        log.info("Sending fake response");
        return newFixedLengthResponse(Response.Status.OK, "text/html",
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "    <head>\n" +
                "        <meta charset=\"utf-8\">\n" +
                "        <title>Nothing to see here</title>\n" +
                "    </head>\n" +
                "    <body>\n" +
                "        <img src=\"https://i.imgflip.com/19e9u5.jpg\" />\n" +
                "    </body>\n" +
                "</html>"
        );
    }

    private boolean notNull(Object obj){
        return obj != null;
    }

    private Response sendFailResponse(String resp){
        return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", resp);
    }

    private Response sendResponse(String resp){
        return newFixedLengthResponse(Response.Status.OK, "text/plain", resp);
    }
}
