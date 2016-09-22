package eu.mikroskeem.moarsms.bukkit;

import eu.mikroskeem.moarsms.API;
import eu.mikroskeem.moarsms.Platform;
import eu.mikroskeem.moarsms.threads.HTTPServerThread;
import eu.mikroskeem.utils.text.MinecraftText;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class plugin extends JavaPlugin {
    private HTTPServerThread httpServerThread;
    private Logger logger = LoggerFactory.getLogger("MoarSMS");
    private Plugin pl = this;

    @Override public void onEnable() {
        saveDefaultConfig();

        logger.info("Setting up platform");
        API.setInstance(new Platform() {
            @Override public void shutdown() {
                if (httpServerThread != null) {
                    httpServerThread.interrupt();
                }
            }

            @Override public Map<String, String> getServiceSecrets() {
                return new HashMap<String, String>(){{
                    getConfig().getConfigurationSection("services").getValues(false).forEach((cServiceId,val)->{
                        put(cServiceId, getConfig().getString(String.format("services.%s.secret", cServiceId)));
                    });
                }};
            }

            @Override public String invokeService(String serviceId, String message) {
                String username = message.trim();
                if(!MinecraftText.validateUsername(username)){
                    return this.getMessage("badmessage.badUsername");
                }
                getServiceCommands(serviceId).forEach(command->{
                    getServer().getScheduler().runTask(pl, ()->runCommand(command.replaceAll("%user%", username)));
                });
                return this.getMessage("success.thanks");
            }

            @Override public boolean allowTest() {
                return getConfig().getBoolean("config.allowTest", false);
            }

            @Override public String getMessage(String path) {
                return getConfig().getString(String.format("messages.%s", path), "");
            }
        });

        logger.info("Starting HTTP thread");
        (httpServerThread = new HTTPServerThread(
                getConfig().getString("config.http.host"),
                getConfig().getInt("config.http.port")
        )).start();

        logger.info("Plugin is ready!");
    }

    @Override public void onDisable() {
        logger.info("Shutting down HTTP thread...");
        API.getInstance().shutdown();

        logger.info("Plugin is disabled!");
    }

    /* Run console command */
    private void runCommand(String command){
        getServer().dispatchCommand(getServer().getConsoleSender(), command);
    }

    /* Get service and commands */
    private List<String> getServiceCommands(String serviceId){
        return getConfig().getStringList(String.format("services.%s.commands", serviceId));
    }
}
