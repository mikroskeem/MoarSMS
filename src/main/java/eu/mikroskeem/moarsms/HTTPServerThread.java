package eu.mikroskeem.moarsms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public final class HTTPServerThread extends Thread {
    private FortumoRequestHandler httpServer;
    private final String host;
    private final int port;

    @Override public void run(){
        log.debug("Initializing HTTP server");
        httpServer = new FortumoRequestHandler(host, port);
        log.debug("Starting HTTP server");
        try {
            httpServer.start();
        } catch (IOException e){
            log.error("Failed to start HTTP server!");
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

        log.debug("Shutting down HTTP server");
        httpServer.closeAllConnections();
        httpServer.stop();
    }
}
