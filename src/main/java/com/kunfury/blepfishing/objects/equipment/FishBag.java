package com.kunfury.blepfishing.objects.equipment;

import com.kunfury.blepfishing.config.ConfigHandler;
import com.kunfury.blepfishing.database.Database;
import com.kunfury.blepfishing.helpers.Formatting;
import com.kunfury.blepfishing.helpers.Utilities;
import com.kunfury.blepfishing.objects.FishObject;
import com.kunfury.blepfishing.objects.FishType;
import com.kunfury.blepfishing.ui.panels.FishBagPanel;
import com.kunfury.blepfishing.helpers.ItemHandler;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class FishBag {
    public final int Id;
    public boolean Pickup;
    private int amount;
    private int tier;
    private ItemStack bagItem;

    public boolean ConfirmSell;

    private FishBag(boolean dummy) {
        Id = -1;
        tier = 1;
        Pickup = true;
    }

    public FishBag() {
        this.Id = -1;
        this.tier = 1;
        this.Pickup = true;
        this.fishList = new ArrayList<>();
    }

    private boolean validateBag(Player player) {
        if (!Database.FishBags.Exists(Id)) {
            InvalidateCache(Id);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Your Fish Bag no longer exists!");
            }
            return false;
        }
        return true;
    }

    public FishBag(ResultSet rs) throws SQLException {
        Id = rs.getInt("id");
        tier = rs.getInt("tier");
        Pickup = rs.getBoolean("pickup");
        RequestUpdate();
    }

    public void UpdateBagItem() {
        if (bagItem == null) {
            Utilities.Severe("Tried to update null bag item");
            return;
        }
        bagItem = createOrUpdateBagItem(bagItem, getBagName(), GenerateLore());
    }

    public String getBagName() {
        switch (tier) {
            case 1: return Formatting.GetLanguageString("Equipment.Fish Bag.tier1Title");
            case 2: return Formatting.GetLanguageString("Equipment.Fish Bag.tier2Title");
            case 3: return Formatting.GetLanguageString("Equipment.Fish Bag.tier3Title");
            case 4: return Formatting.GetLanguageString("Equipment.Fish Bag.tier4Title");
            case 5: return Formatting.GetLanguageString("Equipment.Fish Bag.tier5Title");
            default: return "Error Bag: " + tier;
        }
    }

    public int getAmount() { return amount; }
    public int getTier() { return tier; }

    public void TogglePickup(ItemStack bag, Player player) {
        Pickup = !Pickup;
        Database.FishBags.Update(Id, "pickup", Pickup);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        ItemMeta meta = bag.getItemMeta();
        if (meta == null) return;

        meta.setUnbreakable(true);
        if (Pickup) {
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.pickupEnabled"));
        } else {
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.pickupDisabled"));
        }

        bag.setItemMeta(meta);
        player.getInventory().setItemInMainHand(bag);
    }

    public void FillFromInventory(Player player) {
        if (player == null || !validateBag(player)) return;

        Inventory inv = player.getInventory();
        if (inv == null) return;

        if (fishList == null) fishList = new ArrayList<>();

        int totalDeposited = 0;
        int max = getMax();
        List<ItemStack> toRemove = new ArrayList<>();

        for (ItemStack item : inv.getStorageContents()) {
            if (item == null || isFull()) continue;

            boolean isFish = item.getType() == ItemHandler.FishMat && ItemHandler.hasTag(item, ItemHandler.FishIdKey);

            if (isFish) {
                int space = max - amount;
                int toDeposit = Math.min(space, item.getAmount());

                if (toDeposit > 0) {
                    FishObject template = FishObject.GetCaughtFish(ItemHandler.getTagInt(item, ItemHandler.FishIdKey));
                    if (template != null) {
                        for (int i = 0; i < toDeposit; i++) {
                            FishObject newFish = template.clone();
                            newFish.setFishBagId(Id);
                            fishList.add(newFish);
                        }
                        amount = fishList.size();
                        totalDeposited += toDeposit;
                        if (item.getAmount() == toDeposit) toRemove.add(item);
                        else item.setAmount(item.getAmount() - toDeposit);
                    }
                }
            } else {
                if (TryUpgrade(item)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                    totalDeposited++;
                }
            }
        }

        for (ItemStack item : toRemove) inv.remove(item);

        if (totalDeposited > 0) {
            fishList.sort(Comparator.comparingDouble(FishObject::getScore));
            UpdateBagItem();
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.addFish").replace("{amount}", String.valueOf(totalDeposited)));
            player.playSound(player.getLocation(), Sound.ENTITY_SALMON_FLOP, 0.25f, 0.25f);
        } else if (isFull()) {
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.noSpace"));
        } else {
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.noFish"));
        }
    }

    public int Deposit(ItemStack item, Player player) {
        if (item == null || !validateBag(player)) return 0;

        if (!ItemHandler.hasTag(item, ItemHandler.FishIdKey)) {
            if (TryUpgrade(item)) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                return 1;
            }
            return 0;
        }

        if (isFull()) return 0;

        FishObject fishTemplate = FishObject.GetCaughtFish(ItemHandler.getTagInt(item, ItemHandler.FishIdKey));
        if (fishTemplate == null) return 0;

        int space = getMax() - getAmount();
        int toDeposit = Math.min(space, item.getAmount());
        if (toDeposit <= 0) return 0;

        for (int i = 0; i < toDeposit; i++) {
            FishObject newFish = fishTemplate.clone();
            newFish.setFishBagId(Id);
            fishList.add(newFish);
        }

        fishList.sort(Comparator.comparingDouble(FishObject::getScore));
        amount = fishList.size();
        UpdateBagItem();

        int remaining = item.getAmount() - toDeposit;
        if (remaining > 0) item.setAmount(remaining);
        else player.getInventory().remove(item);

        player.playSound(player.getLocation(), Sound.ENTITY_SALMON_FLOP, 0.25f, 0.25f);
        return toDeposit;
    }

    Map<Integer, Material> upgradeMaterials = new HashMap<Integer, Material>() {{
        put(1, Material.IRON_BLOCK);
        put(2, Material.GOLD_BLOCK);
        put(3, Material.DIAMOND_BLOCK);
        put(4, Material.NETHERITE_BLOCK);
    }};

    private ItemStack createOrUpdateBagItem(ItemStack item, String displayName, List<String> lore) {
        if (item == null) item = new ItemStack(ItemHandler.BagMat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().set(ItemHandler.FishBagId, PersistentDataType.INTEGER, Id);
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        meta.setCustomModelData(ItemHandler.BagModelData);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        item.setItemMeta(meta);
        return item;
    }

    private boolean TryUpgrade(ItemStack item) {
        if (!isFull()) return false;

        boolean upgraded = false;
        switch (item.getType()) {
            case IRON_BLOCK:
                if (tier == 1) { tier = 2; upgraded = true; }
                break;
            case GOLD_BLOCK:
                if (tier == 2) { tier = 3; upgraded = true; }
                break;
            case DIAMOND_BLOCK:
                if (tier == 3) { tier = 4; upgraded = true; }
                break;
            case NETHERITE_BLOCK:
                if (tier == 4) { tier = 5; upgraded = true; }
                break;
            default: break;
        }

        if (upgraded) {
            item.setAmount(item.getAmount() - 1);
            Database.FishBags.Update(Id, "tier", tier);
            UpdateBagItem();
            return true;
        }
        return false;
    }

    public void Withdraw(Player player, FishType type, boolean large, boolean single, int page) {
        if (!Database.FishBags.Exists(Id)) {
            InvalidateCache(Id);
            player.sendMessage(ChatColor.RED + "Your Fish Bag no longer exists!");
            return;
        }

        List<FishObject> filteredFishList = getFish().stream()
                .filter(f -> Objects.equals(f.TypeId, type.Id))
                .collect(Collectors.toList());

        if (!filteredFishList.isEmpty()) {
            int freeSlots = Utilities.getFreeSlots(player.getInventory());
            if (single && freeSlots > 1) freeSlots = 1;
            else if (freeSlots > filteredFishList.size()) freeSlots = filteredFishList.size();
            
            if (large) Collections.reverse(filteredFishList);

            for (int i = 0; i < freeSlots; i++) {
                FishObject fish = filteredFishList.get(i);
                RemoveFish(fish);
                player.getInventory().addItem(fish.CreateItemStack());
                player.playSound(player.getLocation(), Sound.ENTITY_SALMON_FLOP, 0.5f, 1f);
            }

            UpdateBagItem();
            new FishBagPanel(this, page).Show(player);
        }
    }

    public ArrayList<String> GenerateLore() {
        double maxSize = getMax();
        ArrayList<String> lore = new ArrayList<>();
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.descSmall"));
        lore.add("");

        int filledBars = (int) Math.round(10 * ((double) getAmount() / maxSize));
        int emptyBars = 10 - filledBars;

        StringBuilder progressBar = new StringBuilder();
        for (int i = 0; i < filledBars; i++) progressBar.append(ChatColor.GREEN).append("|");
        for (int i = 0; i < emptyBars; i++) progressBar.append(ChatColor.WHITE).append("|");

        lore.add(progressBar.toString() + " " + Formatting.toBigNumber(amount) + "/" + Formatting.toBigNumber(maxSize));
        lore.add("");
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.autoPickup"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.depositAll"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.openBag"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.openPanel"));

        if (amount >= maxSize) {
            Material material = upgradeMaterials.get(tier);
            if (material != null) {
                lore.add("");
                lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.upgrade").replace("{item}", material.name()));
            }
        }
        return lore;
    }

    public int getMax() {
        return (tier >= 1 && tier <= 5) ? 16 * (1 << (3 * tier)) * 2 : 16 * 8 * 2;
    }

    public boolean isFull() { return getMax() <= amount; }

    private List<FishObject> fishList = new ArrayList<>();
    public List<FishObject> getFish() { return fishList; }

    public void RequestUpdate() {
        fishList = Database.FishBags.GetAllFish(Id);
        fishList.sort(Comparator.comparingDouble(FishObject::getScore));
        amount = fishList.size();
    }

    public void AddFish(FishObject fish) {
        fish.setFishBagId(Id);
        fishList.add(fish);
        fishList.sort(Comparator.comparingDouble(FishObject::getScore));
        amount = fishList.size();
        UpdateBagItem();
    }

    public void RemoveFish(FishObject fish) {
        fish.setFishBagId(null);
        fishList.remove(fish);
        amount = fishList.size();
        UpdateBagItem();
    }

    public ItemStack GetItem() {
        return createOrUpdateBagItem(bagItem, getBagName(), GenerateLore());
    }

    private static final Map<Integer, FishBag> FishBags = new HashMap<>();

    public static FishBag GetBag(int bagId) {
        if (bagId <= 0) return null;
        FishBag cached = FishBags.get(bagId);
        if (cached != null) {
            if (Database.FishBags.Exists(bagId)) return cached;
            else {
                FishBags.remove(bagId);
                return null;
            }
        }
        if (!Database.FishBags.Exists(bagId)) return null;
        FishBag bag = Database.FishBags.Get(bagId);
        if (bag != null) FishBags.put(bagId, bag);
        return bag;
    }

    public static FishBag GetBag(ItemStack bagItem) {
        if (!IsBag(bagItem)) return null;
        int bagId = ItemHandler.getTagInt(bagItem, ItemHandler.FishBagId);
        FishBag bag = GetBag(bagId);
        if (bag != null) bag.bagItem = bagItem;
        return bag;
    }

    public static FishBag GetBag(Player player) {
        if (!ConfigHandler.instance.baseConfig.getEnableFishBags() ||
                !player.getInventory().contains(ItemHandler.BagMat)) return null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (!IsBag(item)) continue;
            FishBag bag = GetBag(item);
            if (bag == null || !bag.Pickup || bag.isFull()) continue;
            bag.bagItem = item;
            return bag;
        }
        return null;
    }

    public static boolean IsBag(ItemStack bag) {
        if (bag == null || !bag.hasItemMeta()) return false;
        return bag.getType() == ItemHandler.BagMat &&
                bag.getItemMeta().getPersistentDataContainer().has(ItemHandler.FishBagId, PersistentDataType.INTEGER);
    }

    public static void InvalidateCache(int bagId) {
        FishBags.remove(bagId);
    }

    public static ItemStack GetRecipeItem() {
        FishBag dummy = new FishBag();
        return dummy.createOrUpdateBagItem(
                new ItemStack(ItemHandler.BagMat),
                Formatting.GetLanguageString("Equipment.Fish Bag.tier1Title"),
                Arrays.asList(
                        Formatting.GetLanguageString("Equipment.Fish Bag.descSmall"),
                        "",
                        Formatting.GetLanguageString("Equipment.Fish Bag.autoPickup"),
                        Formatting.GetLanguageString("Equipment.Fish Bag.depositAll"),
                        Formatting.GetLanguageString("Equipment.Fish Bag.openBag"),
                        Formatting.GetLanguageString("Equipment.Fish Bag.openPanel")
                )
        );
    }

    public static Integer GetId(ItemStack bag) {
        return ItemHandler.getTagInt(bag, ItemHandler.FishBagId);
    }
}
