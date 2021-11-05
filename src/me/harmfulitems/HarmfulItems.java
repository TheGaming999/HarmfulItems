package me.harmfulitems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import me.harmfulitems.CollectionUtils.PaginatedList;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

public class HarmfulItems extends JavaPlugin implements Listener {

	private Map<Material, Double> items;
	private List<String> helpMessage;
	private String noPermissionMessage;
	
	private String colorize(String textToTranslate) {
		return ChatColor.translateAlternateColorCodes('&', textToTranslate);
	}
	
	private List<String> colorize(List<String> text) {
		List<String> newList = new ArrayList<>();
		text.forEach(line -> {
			newList.add(colorize(line));
		});
		return newList;
	}
	
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		getConfig().options().copyDefaults(true);
		saveDefaultConfig();
		items = new HashMap<>();
		getConfig().getConfigurationSection("items").getKeys(false).forEach(key -> {
			Material mat = XMaterial.matchXMaterial(key).get().parseMaterial();
			double damage = getConfig().getDouble("items." + key);
			items.put(mat, damage);
		});
		helpMessage = colorize(Arrays.asList("&7[&cHarmfulItems&7]",
				"&c/hi help &8- &7shows this message",
				"&c/hi info &8- &7get the damage that the item on your hand deals.",
				"&c/hi info [item] &8- &7get the damage dealt by the specified item.",
				"&c/hi set <damage> &8- &7set the attacking damage of the item on your hand.",
				"&c/hi set [item] <damage> &8- &7set the attack damage of an item.",
				"&c/hi list [page] &8- &7list items with their attack damage.",
				"&c/hi reload &8- &7load the changes you made in the config file."));
		noPermissionMessage = colorize("&cSorry, but you don't have a permission to execute thihs command.");
		getLogger().info("Enabled.");
	}
	
	public void onDisable() {
		getLogger().info("Disabled.");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(cmd.getLabel().equalsIgnoreCase("harmfulitems")) {
			if(!sender.hasPermission("harmfulitems.use")) {
				sender.sendMessage(noPermissionMessage);
				return true;
			}
			switch(args.length){
			case 0: helpMessage.forEach(sender::sendMessage); break;
			
			case 1:
				if(args[0].equalsIgnoreCase("help")) {
					helpMessage.forEach(sender::sendMessage);
				} else if (args[0].equalsIgnoreCase("info")) {
					if(!(sender instanceof Player)) {
						sender.sendMessage(colorize("&cSorry, but your console doesn't have a hand. Use the following command instead:"));
						sender.sendMessage(colorize("&c/hi info <item>"));
						return true;
					}
					Player p = (Player)sender;
					ItemStack stack = p.getItemInHand();
					if(stack == null) return true;
					Material mat = stack.getType();
					if(mat == null) return true;
					if(items.containsKey(mat)) {
						double damage = items.get(mat);
						sender.sendMessage(colorize("&7The attack damage of &a" + mat.name() + " &7is: &e" + damage + "&7."));
					} else {
						sender.sendMessage(colorize("&cThe item you're holding doesn't have any attacking damage set up!"));
					}
				} else if (args[0].equalsIgnoreCase("set")) {
					sender.sendMessage(colorize("&cThe command is missing some arguments!"));
					sender.sendMessage(colorize("&c/hi set <damage> &7or &c/hi set <item> <damage>"));
				} else if (args[0].equalsIgnoreCase("list")) {
					List<String> list = new LinkedList<>();
					items.keySet().forEach(mat -> {
						list.add(mat.name());
					});
					PaginatedList pagedList = CollectionUtils.paginateListCollectable(list, 10, 1);
					List<String> collectedList = pagedList.collect();
					sender.sendMessage(helpMessage.get(0));
					collectedList.forEach(line -> {
						sender.sendMessage(colorize("&a" + line + " &7- &e" + items.get(XMaterial.matchXMaterial(line).get().parseMaterial())));
					});
					sender.sendMessage(colorize("&7&m   &e[&a" + pagedList.getCurrentPage() + "&8/&a" + pagedList.getFinalPage() + "&e]&7&m   &r"));
				} else if (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("rl")) {
					sender.sendMessage(colorize("&7Reloading config, Hang tight...."));
					Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
					reloadConfig();
					items = new HashMap<>();
					getConfig().getConfigurationSection("items").getKeys(false).forEach(key -> {
						Material mat = XMaterial.matchXMaterial(key).get().parseMaterial();
						double damage = getConfig().getDouble("items." + key);
						items.put(mat, damage);
					});
					sender.sendMessage(colorize("&aReload successful."));
					});
				}
				break;
			case 2:
				if(args[0].equalsIgnoreCase("info")) {
					Optional<XMaterial> xmat = XMaterial.matchXMaterial(args[1].toUpperCase());
					if(xmat.isPresent()) {
						Material mat = xmat.get().parseMaterial();
						if(items.containsKey(mat)) {
							double damage = items.get(mat);
							sender.sendMessage(colorize("&7The attack damage of &a" + mat.name() + " &7is: &e" + damage + "&7."));
						} else {
							sender.sendMessage(colorize("&cThe item you specified doesn't have a damage stored for it."));
						}
					} else {
						sender.sendMessage(colorize("&cCouldn't find an item with such name."));
					}
				} else if (args[0].equalsIgnoreCase("set")) {
					if(!(sender instanceof Player)) {
						sender.sendMessage(colorize("&cSorry Console, but you don't have a hand."));
						return true;
					}
					Player p = (Player)sender;
					ItemStack stack = p.getItemInHand();
					if(stack != null) {
						double newDamage = 0.0D;
						try {
							newDamage = Double.valueOf(args[1]);
						} catch (NumberFormatException ex) {
							sender.sendMessage(colorize("&cSorry, but the number you wrote is wrong."));
						}
						Material mat = stack.getType();
						if(mat == null || mat == Material.AIR) {
							sender.sendMessage(colorize("&cYou can't change your hand strength like that."));
							return true;
						}
						double oldDamage = 0.0;
						if(items.containsKey(mat)) {
							oldDamage = items.get(mat);
						}
						items.put(mat, newDamage);
						getConfig().set("items." + mat.name(), newDamage);
						saveConfig();
						sender.sendMessage(colorize("&7The attack damage of &a" + mat.name() + " &7has been changed from &e" + oldDamage + " &7to &e" + newDamage + "&7."));
					} else {
						sender.sendMessage(colorize("&cYou can't change your hand strength like that."));
					}
				} else if (args[0].equalsIgnoreCase("list")) {
					int pageNumber = 0;
					try {
						pageNumber = Integer.valueOf(args[1]);
					} catch (NumberFormatException ex) {
						sender.sendMessage(colorize("&cSorry, but you didn't write a number, SIR."));
					}
					List<String> list = new LinkedList<>();
					items.keySet().forEach(mat -> {
						list.add(mat.name());
					});
					PaginatedList pagedList = CollectionUtils.paginateListCollectable(list, 10, pageNumber);
					List<String> collectedList = pagedList.collect();
					sender.sendMessage(helpMessage.get(0));
					collectedList.forEach(line -> {
						sender.sendMessage(colorize("&a" + line + " &7- &e" + items.get(XMaterial.matchXMaterial(line).get().parseMaterial())));
					});
					sender.sendMessage(colorize("&7&m   &e[&a" + pagedList.getCurrentPage() + "&8/&a" + pagedList.getFinalPage() + "&e]&7&m   &r"));
				}
				break;
			case 3:
			if (args[0].equalsIgnoreCase("set")) {
				Optional<XMaterial> xmat = XMaterial.matchXMaterial(args[1].toUpperCase());
				if(xmat.isPresent()) {
					double newDamage = 0.0D;
					try {
						newDamage = Double.valueOf(args[2]);
					} catch (NumberFormatException ex) {
						sender.sendMessage(colorize("&cSorry, but the number you wrote is wrong."));
					}
					Material mat = xmat.get().parseMaterial();
					double oldDamage = 0.0;
					if(items.containsKey(mat)) {
						oldDamage = items.get(mat);
					}
					items.put(mat, newDamage);
					getConfig().set("items." + mat.name(), newDamage);
					saveConfig();
					sender.sendMessage(colorize("&7The attack damage of &a" + mat.name() + " &7has been changed from &e" + oldDamage + " &7to &e" + newDamage + "&7."));
				} else {
					// it's not a material then try to check if the second argument is the material.
					// which means the sender used the first argument as a number.
					Optional<XMaterial> zmat = XMaterial.matchXMaterial(args[2].toUpperCase());
					if(zmat.isPresent()) {
						double newDamage = 0.0D;
						try {
							newDamage = Double.valueOf(args[1]);
						} catch (NumberFormatException ex) {
							sender.sendMessage(colorize("&cSorry, neither of your arguments are valid numbers."));
						}	
						Material mato = zmat.get().parseMaterial();
						double oldDamage = 0.0;
						if(items.containsKey(mato)) {
							oldDamage = items.get(mato);
						}
						items.put(mato, newDamage);
						getConfig().set("items." + mato.name(), newDamage);
						saveConfig();
						sender.sendMessage(colorize("&7The attack damage of &a" + mato.name() + " &7has been changed from &e" + oldDamage + " &7to &e" + newDamage + "&7."));
					} else {
						// the sender didn't put any material.
						sender.sendMessage(colorize("&cCouldn't find an item with such name."));
					}
				}
			}
				break;
			default: helpMessage.forEach(sender::sendMessage); break;
			}
		}
		return true;
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent e) {
		if(e.isCancelled()) return;
		Entity damager = e.getDamager();
		Entity damaged = e.getEntity();
		if(damager == null || damaged == null) return;
		DamageCause cause = e.getCause();
		if(cause == null) return;
		if(!(damager instanceof LivingEntity)) return;
		if(!(damaged instanceof LivingEntity)) return;
		LivingEntity livingDamager = (LivingEntity)damager;
		EntityEquipment ee = livingDamager.getEquipment();
		if(ee == null) return;
		ItemStack handItem = ee.getItemInHand();
		if(handItem == null) return;
		Material mat = handItem.getType();
		if(mat == null) return;
		Double itemDamage = items.get(mat);
		if(itemDamage == null || itemDamage == 0.0) return;
		e.setDamage(itemDamage);
		//livingDamaged.damage(itemDamage, livingDamager);
	}
	
}
