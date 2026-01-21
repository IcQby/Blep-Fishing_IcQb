package com.kunfury.blepfishing.objects.treasure;

import com.kunfury.blepfishing.database.Database;
import com.kunfury.blepfishing.helpers.Formatting;
import com.kunfury.blepfishing.helpers.Utilities;
import com.kunfury.blepfishing.helpers.ItemHandler;
import com.kunfury.blepfishing.objects.FishingArea;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.nio.channels.NotYetBoundException;
import java.time.LocalDateTime;
import java.util.*;

public class CompassPiece extends TreasureType {

    public static List<FishingArea> CompassAreas;

    public CompassPiece(String id, int weight, boolean announce) {
        super(id, weight, announce);
        CompassAreas = FishingArea.GetAll().stream()
                .filter(a -> a.HasCompassPiece)
                .toList();
    }

    @Override
    public ItemStack GetItem() {
        throw new NotYetBoundException();
    }

    @Override
    public boolean CanGenerate(Player player) {
        return true;
    }

    @Override
    protected void Use(ItemStack item, Player player) {
        // TODO: Implement focusing towards the next piece
    }

    @Override
    public String getFormattedName() {
        return Formatting.GetLanguageString("Treasure.Compass Piece.name");
    }

    @Override
    public ItemStack GetItem(PlayerFishEvent e) {
        Player player = e.getPlayer();

        List<FishingArea> fishingAreas = FishingArea.GetAvailableAreas(e.getHook().getLocation()).stream()
                .filter(a -> a.HasCompassPiece)
                .filter(a -> !HasPiece(player, a))
                .toList();

        if (fishingAreas.isEmpty()) return null;

        FishingArea area = fishingAreas.get(0);

        Database.TreasureDrops.Add(new TreasureDrop(
                "compassPiece." + area.Id,
                player.getUniqueId().toString(),
                LocalDateTime.now()
        ));

        return GeneratePiece(new FishingArea[]{area});
    }

    /**
     * Checks if the player already has a compass piece for a specific area.
     * Null-safe and logs problematic items.
     */
    private boolean HasPiece(Player player, FishingArea area) {
        if (!player.getInventory().contains(Material.PRISMARINE_SHARD)) return false;

        for (ItemStack item : player.getInventory()) {
            if (item == null || item.getType() != Material.PRISMARINE_SHARD) continue;
            if (!ItemHandler.hasTag(item, ItemHandler.FishAreaId)) continue;

            String areaIdArray = ItemHandler.getTagString(item, ItemHandler.FishAreaId);
            if (areaIdArray == null || areaIdArray.isEmpty()) continue;

            for (String areaId : areaIdArray.split(",\\s*")) {
                FishingArea a = FishingArea.FromId(areaId);
                if (a == null) {
                    Utilities.Severe("Invalid Area ID in compass piece: " + areaId + " (player: " + player.getName() + ")");
                    continue;
                }
                if (area.equals(a)) return true;
            }
        }
        return false;
    }

    public static ItemStack GeneratePiece(FishingArea[] areas) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = new ArrayList<>();
        StringBuilder areaIds = new StringBuilder();

        for (FishingArea a : areas) {
            lore.add(ChatColor.YELLOW + a.Name);
            if (areaIds.length() > 0) areaIds.append(", ");
            areaIds.append(a.Id);
        }

        String title;
        if (areas.length == 1) {
            title = Formatting.GetLanguageString("Treasure.Compass Piece.name");
        } else {
            title = Formatting.GetLanguageString("Treasure.Compass Piece.nameMulti")
                    .replace("{amount}", String.valueOf(areas.length))
                    .replace("{total}", String.valueOf(FishingArea.GetCompassAreas().size()));
        }

        meta.displayName(Formatting.nameHandler(title));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ItemHandler.TreasureTypeId, PersistentDataType.STRING, "compassPiece");
        meta.getPersistentDataContainer().set(ItemHandler.FishAreaId, PersistentDataType.STRING, areaIds.toString());

        // Cosmetic glow
        meta.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack GenerateCompass() {
        ItemStack compassItem = new ItemStack(Material.COMPASS);
        CompassMeta compassMeta = (CompassMeta) compassItem.getItemMeta();
        if (compassMeta == null) return compassItem;

        compassMeta.displayName(Formatting.nameHandler("Treasure.Compass.name"));

        List<String> lore = new ArrayList<>();
        lore.add(Formatting.GetLanguageString("Treasure.Compass.lore"));
        lore.add("");
        lore.add(Formatting.GetLanguageString("Treasure.Compass.use"));
        compassMeta.lore(lore);

        // Cosmetic glow
        compassMeta.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1);
        compassMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        compassMeta.getPersistentDataContainer().set(ItemHandler.CompassKey, PersistentDataType.INTEGER, -1);

        compassItem.setItemMeta(compassMeta);
        return compassItem;
    }

    public static void FocusCompass(ItemStack compassItem) {
        CompassMeta compassMeta = (CompassMeta) compassItem.getItemMeta();
        if (compassMeta == null) return;

        int compassId = ItemHandler.getTagInt(compassItem, ItemHandler.CompassKey);
        Location allBlueLoc = Database.AllBlues.Get(compassId);

        if (allBlueLoc == null) {
            Utilities.Severe("Tried to Focus Compass with invalid All Blue");
            return;
        }

        if (allBlueLoc.getBlock().getType() != Material.LODESTONE)
            allBlueLoc.getBlock().setType(Material.LODESTONE);

        compassMeta.setLodestone(allBlueLoc);
        compassItem.setItemMeta(compassMeta);
    }

    public static ItemStack Combine(ItemStack[] craftComponents) {
        List<FishingArea> areas = new ArrayList<>();
        for (ItemStack item : craftComponents) {
            if (item == null) continue;

            String areaIdArray = ItemHandler.getTagString(item, ItemHandler.FishAreaId);
            if (areaIdArray == null || areaIdArray.isEmpty()) return null;

            for (String areaId : areaIdArray.split(",\\s*")) {
                FishingArea a = FishingArea.FromId(areaId);
                if (a == null) {
                    Utilities.Severe("Invalid Area ID found in compass piece: " + areaId);
                    return null;
                }
                if (!areas.contains(a)) areas.add(a);
            }
        }

        if (new HashSet<>(areas).containsAll(CompassAreas))
            return GenerateCompass();

        if (areas.size() <= 1) return null; // ensures pieces are actually being combined

        return GeneratePiece(areas.toArray(new FishingArea[0]));
    }

    public static boolean IsPiece(ItemStack item) {
        return item != null && item.getType() == Material.PRISMARINE_SHARD
                && ItemHandler.hasTag(item, ItemHandler.FishAreaId);
    }

    public static boolean isCompass(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS
                && ItemHandler.hasTag(item, ItemHandler.CompassKey);
    }
}
