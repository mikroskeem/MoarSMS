package eu.mikroskeem.moarsms.threads;

import eu.mikroskeem.moarsms.FortumoRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class HTTPServerThread extends Thread {
    private Logger logger;
    private FortumoRequestHandler httpServer;
    private String host;
    private int port;

    public HTTPServerThread(String host, int port){
        super("HTTPServerThread");
        logger = LoggerFactory.getLogger(this.getClass());
        this.host = host;
        this.port = port;
    }

    @Override public void run(){
        logger.debug("Initializing HTTP server");
        httpServer = new FortumoRequestHandler(host, port);
        logger.debug("Starting HTTP server");
        try {
            httpServer.start();
        } catch (IOException e){
            logger.error("Failed to start HTTP server!");
            return;
        }

        while(true){
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (InterruptedException e){
                break;
            }
        }

        logger.debug("Shutting down HTTP server");
        httpServer.stop();
    }
}
