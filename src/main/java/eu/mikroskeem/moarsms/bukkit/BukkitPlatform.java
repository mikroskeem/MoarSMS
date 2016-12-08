package eu.mikroskeem.moarsms.bukkit;

import eu.mikroskeem.moarsms.HTTPServerThread;
import eu.mikroskeem.moarsms.Platform;
import eu.mikroskeem.utils.text.MinecraftText;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class BukkitPlatform implements Platform {
    private final HTTPServerThread httpServerThread;
    private final plugin pl;
    @Override public void shutdown() {
        if (httpServerThread != null) {
            httpServerThread.interrupt();
        }
    }

    @Override public Map<String, String> getServiceSecrets() {
        return new HashMap<String, String>(){{
            pl.getConfig().getConfigurationSection("services").getValues(false).forEach((cServiceId,val)->{
                put(cServiceId, pl.getConfig().getString(String.format("services.%s.secret", cServiceId)));
            });
        }};
    }

    @Override public String invokeService(String serviceId, String message) {
        String username = message.trim();
        if(!MinecraftText.validateUsername(username)){
            return this.getMessage("badmessage.badUsername");
        }
        pl.getServiceCommands(serviceId).forEach(command->{
            pl.getServer().getScheduler().runTask(pl, ()->pl.runCommand(command.replaceAll("%user%", username)));
        });
        return this.getMessage("success.thanks");
    }

    @Override public boolean allowTest() {
        return pl.getConfig().getBoolean("config.allowTest", false);
    }

    @Override public String getMessage(String path) {
        return pl.getConfig().getString(String.format("messages.%s", path), "");
    }
}
