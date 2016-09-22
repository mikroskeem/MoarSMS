package eu.mikroskeem.moarsms;

import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FortumoRequestHandler extends NanoHTTPD {
    private Map<String, String> serviceSecrets;
    private boolean allowTest;
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public FortumoRequestHandler(String host, int port){
        super(host, port);
        serviceSecrets = API.getInstance().getServiceSecrets();
        allowTest = API.getInstance().allowTest();
    }

    @Override public Response serve(IHTTPSession session) {
        if(!session.getUri().equals("/")){
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }
        logger.info("Beep-boop, got request");
        Map<String, String> params = session.getParms();

        String originIP = session.getHeaders().get("x-real-ip");
        if(originIP != null){
            String nginxProxy = session.getHeaders().get("x-nginx-proxy");
            if (nginxProxy == null || !nginxProxy.equals("true")) {
                logger.info("X-Forwaded-For was present, but X-Nginx-Proxy not, bailing out!");
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
        logger.info("Origin IP: {}", originIP);
        logger.info("Useragent: {}", useragent);
        logger.info("Sender: {}", sender);
        logger.info("Keyword: {}", keyword);
        logger.info("Message: {}", message);
        logger.info("Test: {}", test);

        /* Check parameters */
        if(notNull(signature) && notNull(serviceId) && notNull(keyword) && notNull(message)){
            logger.info("Got valid Fortumo request!");
            /* Check for IP */
            if(!FortumoUtils.checkIP(originIP)){
                logger.info("Request was from non-whitelisted IP '{}'!", originIP);
                return sendFailResponse(API.getInstance().getMessage("validation.forbiddenIP"));
            }
            /* Check for service id */
            String checkSignature = serviceSecrets.get(serviceId);
            if(checkSignature == null){
                logger.info("Service '{}' was requested, but it is not defined!", serviceId);
                logger.info("Keyword was '{}', maybe this helps", keyword);
                return sendFailResponse(API.getInstance().getMessage("validation.undefinedService"));
            }

            /* Check for signature */
            if(!FortumoUtils.checkSignature(params, checkSignature)){
                logger.info("Signature seems incorrect, correct is '{}', but '{}' was provided",
                        checkSignature, signature);
                return sendFailResponse(API.getInstance().getMessage("validation.signatureIncorrect"));
            }

            /* Check if message it's test message and if they're allowed */
            if(test.equals("true") && !allowTest){
                logger.info("Test messages are disabled from config, bailing out");
                return sendResponse(API.getInstance().getMessage("test.notallowed"));
            }

            logger.info("Message is valid");
            return sendResponse(API.getInstance().invokeService(serviceId, message));
        }

        logger.info("Sending fake response");
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
