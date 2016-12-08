package eu.mikroskeem.moarsms.bukkit;

import eu.mikroskeem.moarsms.API;
import eu.mikroskeem.moarsms.HTTPServerThread;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

@Slf4j
public final class plugin extends JavaPlugin {
    private HTTPServerThread httpServerThread;
    private plugin pl = this;

    @Override public void onEnable() {
        saveDefaultConfig();

        log.info("Setting up platform");
        API.setInstance(new BukkitPlatform(httpServerThread, pl));

        log.info("Starting HTTP thread");
        (httpServerThread = new HTTPServerThread(
                getConfig().getString("config.http.host"),
                getConfig().getInt("config.http.port")
        )).start();
        log.info("Plugin is ready!");
    }

    @Override public void onDisable() {
        log.info("Shutting down HTTP thread...");
        API.getInstance().shutdown();

        log.info("Plugin is disabled!");
    }

    /* Run console command */
    void runCommand(String command){
        getServer().dispatchCommand(getServer().getConsoleSender(), command);
    }

    /* Get service and commands */
    List<String> getServiceCommands(String serviceId){
        return getConfig().getStringList(String.format("services.%s.commands", serviceId));
    }
}
