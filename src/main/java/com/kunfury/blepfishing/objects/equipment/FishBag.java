package com.kunfury.blepfishing.objects.equipment;

import com.kunfury.blepfishing.config.ConfigHandler;
import com.kunfury.blepfishing.database.Database;
import com.kunfury.blepfishing.helpers.Formatting;
import com.kunfury.blepfishing.helpers.Utilities;
import com.kunfury.blepfishing.objects.FishObject;
import com.kunfury.blepfishing.objects.FishType;
import com.kunfury.blepfishing.ui.panels.FishBagPanel;
import com.kunfury.blepfishing.helpers.ItemHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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

    /**
     * Validates that this bag still exists in the database.
     * If it doesn't, removes it from the cache.
     * @return true if valid, false if bag was deleted
     */
    private boolean validateBag(Player player) {
        if (!Database.FishBags.Exists(Id)) {
            // Remove from cache if present
            InvalidateCache(Id);

            // Notify player if provided
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

        //Bukkit.broadcastMessage("Requesting Update From ResultSet Instantiating");
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
        switch(tier) {
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
        // Toggle the pickup state
        Pickup = !Pickup;

        // Update the database
        Database.FishBags.Update(Id, "pickup", Pickup);

        // Play a sound for feedback
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        // Get the item meta safely
        ItemMeta meta = bag.getItemMeta();
        if (meta == null) return;

        // Always make the bag unbreakable
        meta.setUnbreakable(true);

        // Update the player with a message
        if (Pickup) {
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.pickupEnabled"));
        } else {
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.pickupDisabled"));
        }

        // Apply changes to the bag
        bag.setItemMeta(meta);

        // Update the player's main hand
        player.getInventory().setItemInMainHand(bag);
    }

    public void FillFromInventory(Player player) {
        if (player == null) return;

        // Validate bag
        if (!validateBag(player)) return;

        Inventory inv = player.getInventory();
        if (inv == null) return;

        if (fishList == null) fishList = new ArrayList<>();

        int totalDeposited = 0;
        int max = getMax(); // cache max size
        List<ItemStack> toRemove = new ArrayList<>();

        // Always iterate over a copy to avoid ConcurrentModificationException
        for (ItemStack item : inv.getStorageContents()) {
            if (item == null) continue;
            if (isFull()) break; // bag full, stop

            boolean isFish = item.getType() == ItemHandler.FishMat && ItemHandler.hasTag(item, ItemHandler.FishIdKey);

            if (isFish) {
                int space = max - amount;
                int toDeposit = Math.min(space, item.getAmount());

                if (toDeposit > 0) {
                    FishObject template = FishObject.GetCaughtFish(ItemHandler.getTagInt(item, ItemHandler.FishIdKey));
                    if (template != null) {
                        List<FishObject> batch = new ArrayList<>(toDeposit);
                        for (int i = 0; i < toDeposit; i++) {
                            FishObject newFish = template.clone();
                            newFish.setFishBagId(Id);
                            batch.add(newFish);
                        }
                        fishList.addAll(batch);

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

        toRemove.forEach(inv::remove);

        if (totalDeposited > 0) UpdateBagItem();

        if (totalDeposited > 0) {
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.addFish")
                    .replace("{amount}", String.valueOf(totalDeposited)));
            player.playSound(player.getLocation(), Sound.ENTITY_SALMON_FLOP, 0.25f, 0.25f);
        } else if (isFull()) {
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.noSpace"));
        } else {
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.noFish"));
        }
    }

    public int Deposit(ItemStack item, Player player) {
        if (item == null) return 0;

        // Validate bag
        if (!validateBag(player)) return 0;

        // Upgrade if non-fish
        if (!ItemHandler.hasTag(item, ItemHandler.FishIdKey)) {
            if (TryUpgrade(item)) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                return 1; // consumed
            }
            return 0;
        }

        if (isFull()) return 0;

        // Deposit fish (rest of your existing code)
        FishObject fishTemplate = FishObject.GetCaughtFish(ItemHandler.getTagInt(item, ItemHandler.FishIdKey));
        if (fishTemplate == null) return 0;

        int space = getMax() - getAmount();
        int toDeposit = Math.min(space, item.getAmount());
        if (toDeposit <= 0) return 0;

        List<FishObject> batch = new ArrayList<>(toDeposit);
        for (int i = 0; i < toDeposit; i++) {
            FishObject newFish = fishTemplate.clone();
            newFish.setFishBagId(Id);
            batch.add(newFish);
        }
        fishList.addAll(batch);

        amount = fishList.size();
        UpdateBagItem();

        int remaining = item.getAmount() - toDeposit;
        if (remaining > 0) item.setAmount(remaining);
        else player.getInventory().remove(item);

        player.playSound(player.getLocation(), Sound.ENTITY_SALMON_FLOP, 0.25f, 0.25f);

        return toDeposit;
    }

    Map<Integer, Material> upgradeMaterials = Map.of(
            1, Material.IRON_BLOCK,
            2, Material.GOLD_BLOCK,
            3, Material.DIAMOND_BLOCK,
            4, Material.NETHERITE_BLOCK
    );

    private ItemStack createOrUpdateBagItem(ItemStack item, String displayName, List<String> lore) {
        if (item == null) item = new ItemStack(ItemHandler.BagMat);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set persistent data
        meta.getPersistentDataContainer().set(ItemHandler.FishBagId, PersistentDataType.INTEGER, Id);

        // Set display name and lore
        meta.setDisplayName(displayName);
        meta.setLore(lore);

        // Custom model + flags
        meta.setCustomModelData(ItemHandler.BagModelData);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        item.setItemMeta(meta);
        return item;
    }

private boolean TryUpgrade(ItemStack item) {
        if (!isFull()) return false;

        switch (item.getType()) {
            case IRON_BLOCK:
                if (tier != 1) return false;
                tier = 2;
                break;
            case GOLD_BLOCK:
                if (tier != 2) return false;
                tier = 3;
                break;
            case DIAMOND_BLOCK:
                if (tier != 3) return false;
                tier = 4;
                break;
            case NETHERITE_BLOCK:
                if (tier != 4) return false;
                tier = 5;
                break;
            default:
                return false;
        }

        item.setAmount(item.getAmount() - 1);
        Database.FishBags.Update(Id, "tier", tier);
        UpdateBagItem();
        return true;
    }

    public void Withdraw(Player player, FishType type, boolean large, boolean single, int page) {
        // Check if bag exists
        if (!Database.FishBags.Exists(Id)) {
            InvalidateCache(Id);
            player.sendMessage(ChatColor.RED + "Your Fish Bag no longer exists!");
            return;
        }

        List<FishObject> filteredFishList = new ArrayList<>(
                getFish().stream()
                        .filter(f -> Objects.equals(f.TypeId, type.Id))
                        .toList()
        );

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

        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.descSmall")); //TODO: Change to dynamic based on size of bag
        lore.add("");

        double barScore = 0;

        if (getAmount() != 0 || maxSize != 0) {
            barScore = 10.0 * ((double) getAmount() / maxSize);
        }

        int filledBars = (int) Math.round(10 * ((double) getAmount() / getMax()));
        int emptyBars = 10 - filledBars;

        StringBuilder progressBar = new StringBuilder();
        for (int i = 0; i < filledBars; i++) progressBar.append(ChatColor.GREEN).append("|");
        for (int i = 0; i < emptyBars; i++) progressBar.append(ChatColor.WHITE).append("|");

        lore.add(progressBar + " " + Formatting.toBigNumber(amount) + "/" + Formatting.toBigNumber(maxSize));

        lore.add("");
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.autoPickup"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.depositAll"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.openBag"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.openPanel"));

        if (amount >= getMax()) {
            var material = upgradeMaterials.getOrDefault(tier, null);
            if (material != null) {
                lore.add("");

                lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.upgrade")
                        .replace("{item}", material.name()));
            }
        }

        return lore;
    }

    public int getMax() {
        return (tier >= 1 && tier <= 5) ? 16 * (1 << (3 * tier)) * 2 : 16 * 8 * 2;
    }

    public boolean isFull() {
        return getMax() <= amount;
    }

    private List<FishObject> fishList = new ArrayList<>();
    public List<FishObject> getFish() {
        return fishList;
    }

    // Refreshes the fish list from the database, keeps it sorted
    public void RequestUpdate() {
        // Get all fish from DB
        fishList = Database.FishBags.GetAllFish(Id);

        // Sort by score
        fishList.sort(Comparator.comparingDouble(FishObject::getScore));

        // Update amount
        amount = fishList.size();
    }

    // Add a fish to this bag, keeps list sorted immediately
    public void AddFish(FishObject fish) {
        fish.setFishBagId(Id);
        fishList.add(fish);

        // Keep sorted by score
        fishList.sort(Comparator.comparingDouble(FishObject::getScore));

        // Update amount
        amount = fishList.size();

        // Optionally update the bag item display
        UpdateBagItem();
    }

    // Remove a fish from this bag
    public void RemoveFish(FishObject fish) {
        fish.setFishBagId(null);
        fishList.remove(fish);

        // Update amount
        amount = fishList.size();

        // Optionally update the bag item display
        UpdateBagItem();
    }

    // Returns the ItemStack representing this bag with updated name/lore
    public ItemStack GetItem() {
        return createOrUpdateBagItem(bagItem, getBagName(), GenerateLore());
    }

    /// Static cache and retrieval
    private static final Map<Integer, FishBag> FishBags = new HashMap<>();

    /**
     * Get a FishBag by ID, using the cache. Automatically removes deleted bags from cache.
     */
    public static FishBag GetBag(int bagId) {
        if (bagId <= 0) return null;

        // Check cache first
        FishBag cached = FishBags.get(bagId);
        if (cached != null) {
            if (Database.FishBags.Exists(bagId)) return cached;
            else {
                // Bag deleted from DB, remove from cache
                FishBags.remove(bagId);
                return null;
            }
        }

        // Not cached, fetch from DB
        if (!Database.FishBags.Exists(bagId)) return null;

        FishBag bag = Database.FishBags.Get(bagId);
        if (bag != null) FishBags.put(bagId, bag);

        return bag;
    }

    /**
     * Get a FishBag from an ItemStack (the bag item). Uses the cache.
     */
    public static FishBag GetBag(ItemStack bagItem) {
        if (!IsBag(bagItem)) return null;

        int bagId = ItemHandler.getTagInt(bagItem, ItemHandler.FishBagId);

        FishBag bag = GetBag(bagId); // Unified retrieval through cache
        if (bag != null) bag.bagItem = bagItem; // Keep the reference to the current ItemStack

        return bag;
    }

    /**
     * Get the first usable FishBag from a player’s inventory.
     */
    public static FishBag GetBag(Player player) {
        if (!ConfigHandler.instance.baseConfig.getEnableFishBags() ||
                !player.getInventory().contains(ItemHandler.BagMat)) return null;

        for (ItemStack item : player.getInventory().getContents()) {
            if (!IsBag(item)) continue;

            FishBag bag = GetBag(item); // Uses cache
            if (bag == null) continue;
            if (!bag.Pickup || bag.isFull()) continue;

            bag.bagItem = item;
            return bag;
        }

        return null;
    }

    /**
     * Checks if an ItemStack is a valid FishBag item.
     */
    public static boolean IsBag(ItemStack bag) {
        if (bag == null || !bag.hasItemMeta()) return false;

        return bag.getType() == ItemHandler.BagMat &&
                bag.getItemMeta().getPersistentDataContainer().has(ItemHandler.FishBagId, PersistentDataType.INTEGER);
    }

    /**
     * Optional: manually invalidate a bag from cache (e.g., if deleted from DB)
     */
    public static void InvalidateCache(int bagId) {
        FishBags.remove(bagId);
    }

    /**
     * Create a dummy bag item for recipe / display purposes.
     */
    public static ItemStack GetRecipeItem() {
        FishBag dummy = new FishBag(); // dummy for ID access if needed
        return dummy.createOrUpdateBagItem(
                new ItemStack(ItemHandler.BagMat),
                Formatting.GetLanguageString("Equipment.Fish Bag.tier1Title"),
                List.of(
                        Formatting.GetLanguageString("Equipment.Fish Bag.descSmall"),
                        "",
                        Formatting.GetLanguageString("Equipment.Fish Bag.autoPickup"),
                        Formatting.GetLanguageString("Equipment.Fish Bag.depositAll"),
                        Formatting.GetLanguageString("Equipment.Fish Bag.openBag"),
                        Formatting.GetLanguageString("Equipment.Fish Bag.openPanel")
                )
        );
    }

 /**
     * Get the bag ID from an ItemStack.
     */
    public static Integer GetId(ItemStack bag) {
        return ItemHandler.getTagInt(bag, ItemHandler.FishBagId);
    }

    // Add this to fix the GetRecipeItem() call
    public FishBag() {
        this.Id = -1;
        this.tier = 1;
        this.Pickup = true;
    }
}
