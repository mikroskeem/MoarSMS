package eu.mikroskeem.moarsms.bukkit;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

@RequiredArgsConstructor
public class ReloadCommand implements CommandExecutor {
    private final plugin pl;
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!command.getName().equals("moarsms")) return false;
        pl.reloadConfig();
        sender.sendMessage("Configuration reloaded");
        return true;
    }
}
