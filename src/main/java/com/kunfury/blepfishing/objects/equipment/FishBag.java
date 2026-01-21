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
import org.bukkit.enchantments.Enchantment;
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
import java.util.stream.Collectors;

public class FishBag {
    public final int Id;
    public boolean Pickup;
    private int amount;
    private int tier;
    private ItemStack bagItem;

    public boolean ConfirmSell;

    public FishBag(){
        amount = 0;
        tier = 1;
        Pickup = true;
        Id = Database.FishBags.Add(this);
    }

    public FishBag(ResultSet rs) throws SQLException {
        Id = rs.getInt("id");
        tier = rs.getInt("tier");
        Pickup = rs.getBoolean("pickup");
        RequestUpdate();
    }

    public void Use(Player p, ItemStack bagItem){
        this.bagItem = bagItem;
        new FishBagPanel(this, 1).Show(p);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, .3f, 1f);
    }

    public void UpdateBagItem(){
        if(bagItem == null){
            Utilities.Severe("Tried to update null bag item");
            return;
        }

        ItemMeta m = bagItem.getItemMeta();
        if(m == null) return;

        m.setDisplayName(getBagName());
        m.setLore(GenerateLore());
        m.getPersistentDataContainer().set(ItemHandler.FishBagId, PersistentDataType.INTEGER, Id);

        bagItem.setItemMeta(m);
    }

    public String getBagName(){
        switch(tier){
            case 1: return Formatting.GetLanguageString("Equipment.Fish Bag.tier1Title");
            case 2: return Formatting.GetLanguageString("Equipment.Fish Bag.tier2Title");
            case 3: return Formatting.GetLanguageString("Equipment.Fish Bag.tier3Title");
            case 4: return Formatting.GetLanguageString("Equipment.Fish Bag.tier4Title");
            case 5: return Formatting.GetLanguageString("Equipment.Fish Bag.tier5Title");
            default: return "Error Bag: " + tier;
        }
    }

    public int getAmount(){ return amount; }
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

    public void FillFromInventory(Player player){
        int fillAmt = 0;
        for(ItemStack item : player.getInventory().getStorageContents()){
            if(getAmount() + fillAmt >= getMax()) break;
            if(item != null && item.getType() == ItemHandler.FishMat && ItemHandler.hasTag(item, ItemHandler.FishIdKey)){
                if(Deposit(item, player))
                    fillAmt++;
            }
        }

        if(fillAmt > 0){
            player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.addFish")
                    .replace("{amount}", String.valueOf(fillAmt)));
        } else {
            if(getAmount() >= getMax())
                player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.noSpace"));
            else
                player.sendMessage(Formatting.GetFormattedMessage("Equipment.Fish Bag.noFish"));
            return;
        }
        UpdateBagItem();
    }

    public boolean Deposit(ItemStack item, Player player){
        if (ItemHandler.hasTag(item, ItemHandler.FishIdKey)) {
            if(isFull()) return false;

            FishObject fish = FishObject.GetCaughtFish(ItemHandler.getTagInt(item, ItemHandler.FishIdKey));
            if(fish == null) return false;
            
            AddFish(fish);
            player.getInventory().remove(item);
            player.playSound(player.getLocation(), Sound.ENTITY_SALMON_FLOP, .25f, .25f);
            return true;
        }
        return TryUpgrade(item);
    }

    Map<Integer, Material> upgradeMaterials = new HashMap<Integer, Material>() {{
        put(1, Material.IRON_BLOCK);
        put(2, Material.GOLD_BLOCK);
        put(3, Material.DIAMOND_BLOCK);
        put(4, Material.NETHERITE_BLOCK);
    }};

    private boolean TryUpgrade(ItemStack item){
        if(!isFull()) return false;

        boolean upgraded = false;
        switch (item.getType()){
            case IRON_BLOCK:
                if(tier == 1) { tier = 2; upgraded = true; }
                break;
            case GOLD_BLOCK:
                if(tier == 2) { tier = 3; upgraded = true; }
                break;
            case DIAMOND_BLOCK:
                if(tier == 3) { tier = 4; upgraded = true; }
                break;
            case NETHERITE_BLOCK:
                if(tier == 4) { tier = 5; upgraded = true; }
                break;
            default: return false;
        }

        if(upgraded) {
            item.setAmount(item.getAmount() - 1);
            Database.FishBags.Update(Id, "tier", tier);
            UpdateBagItem();
            return true;
        }
        return false;
    }

    public void Withdraw(Player player, FishType type, boolean large, boolean single, int page){
        List<FishObject> filteredFishList = getFish().stream()
                .filter(f -> Objects.equals(f.TypeId, type.Id))
                .collect(Collectors.toList());

        if(!filteredFishList.isEmpty()){
            int freeSlots = Utilities.getFreeSlots(player.getInventory());
            if(single && freeSlots > 1) freeSlots = 1;
            else if(freeSlots > filteredFishList.size()) freeSlots = filteredFishList.size();
            
            if(large) Collections.reverse(filteredFishList);

            for(int i = 0; i < freeSlots; i++){
                FishObject fish = filteredFishList.get(i);
                RemoveFish(fish);
                player.getInventory().addItem(fish.CreateItemStack());
                player.playSound(player.getLocation(), Sound.ENTITY_SALMON_FLOP, .5f, 1f);
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

        double barScore = 0;
        if(getAmount() != 0 && maxSize != 0){
            barScore = 10.0 * (getAmount() / maxSize);
        }

        StringBuilder progressBar = new StringBuilder();
        for (int i = 1; i <= (int)barScore; i++) {
            progressBar.append(ChatColor.GREEN).append("|");
        }
        for (int i = 0; i < 10 - Math.floor(barScore); i++) {
            progressBar.append(ChatColor.WHITE).append("|");
        }

        lore.add(progressBar.toString() + " " + Formatting.toBigNumber(amount) + "/" + Formatting.toBigNumber(maxSize));
        lore.add("");
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.autoPickup"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.depositAll"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.openBag"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.openPanel"));

        if(amount >= getMax()){
            Material material = upgradeMaterials.get(tier);
            if(material != null){
                lore.add("");
                lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.upgrade")
                        .replace("{item}", material.name()));
            }
        }
        return lore;
    }

    public int getMax(){
        return (int) (16 * Math.pow(8, tier)) * 2;
    }

    public boolean isFull(){
        return getMax() <= amount;
    }

    public void AddFish(FishObject fish){
        fish.setFishBagId(Id);
        RequestUpdate();
    }
    public void RemoveFish(FishObject fish){
        fish.setFishBagId(null);
        RequestUpdate();
    }
    private List<FishObject> fishList = new ArrayList<>();
    public List<FishObject> getFish(){
        return fishList;
    }

    public void RequestUpdate(){
        fishList = Database.FishBags.GetAllFish(Id).stream()
                .sorted(Comparator.comparingDouble(FishObject::getScore))
                .collect(Collectors.toList());
        amount = fishList.size();
    }

    public ItemStack GetItem() {
        ItemStack item = new ItemStack(ItemHandler.BagMat);
        ItemMeta itemMeta = item.getItemMeta();
        if(itemMeta == null) return item;

        itemMeta.getPersistentDataContainer().set(ItemHandler.FishBagId, PersistentDataType.INTEGER, Id);
        itemMeta.setDisplayName(getBagName());
        itemMeta.setLore(GenerateLore());
        itemMeta.setCustomModelData(ItemHandler.BagModelData);
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(itemMeta);

        return item;
    }

    public static FishBag GetBag(int bagId){
        return Database.FishBags.Get(bagId);
    }

    private static final HashMap<Integer, FishBag> FishBags = new HashMap<>();
    public static FishBag GetBag(ItemStack bagItem){
        if(!ItemHandler.hasTag(bagItem, ItemHandler.FishBagId)) return null;

        int bagId = ItemHandler.getTagInt(bagItem, ItemHandler.FishBagId);
        if(FishBags.containsKey(bagId)) return FishBags.get(bagId);
        if(!Database.FishBags.Exists(bagId)) return null;

        FishBag bag = Database.FishBags.Get(bagId);
        FishBags.put(bagId, bag);
        bag.bagItem = bagItem;
        return bag;
    }

    public static FishBag GetBag(Player player){
        if(!ConfigHandler.instance.baseConfig.getEnableFishBags() || !player.getInventory().contains(ItemHandler.BagMat))
            return null;

        for (ItemStack slot : player.getInventory().getContents()){
            if(!IsBag(slot)) continue;
            FishBag bag = GetBag(slot);
            if(bag == null || !bag.Pickup || bag.isFull()) continue;
            bag.bagItem = slot;
            return bag;
        }
        return null;
    }

    public static boolean IsBag(ItemStack bag){
        if(bag == null || !bag.hasItemMeta()) return false;
        return bag.getType() == ItemHandler.BagMat
                && bag.getItemMeta().getPersistentDataContainer().has(ItemHandler.FishBagId, PersistentDataType.INTEGER);
    }

    public static ItemStack GetRecipeItem(){
        ItemStack bag = new ItemStack(ItemHandler.BagMat, 1);
        ItemMeta m = bag.getItemMeta();
        if(m == null) return bag;

        m.getPersistentDataContainer().set(ItemHandler.FishBagId, PersistentDataType.INTEGER, -1);
        m.setDisplayName(Formatting.GetLanguageString("Equipment.Fish Bag.tier1Title"));

        List<String> lore = new ArrayList<>();
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.descSmall"));
        lore.add("");
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.autoPickup"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.depositAll"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.openBag"));
        lore.add(Formatting.GetLanguageString("Equipment.Fish Bag.openPanel"));

        m.setLore(lore);
        m.setCustomModelData(ItemHandler.BagModelData);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        bag.setItemMeta(m);
        bag.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);

        return bag;
    }

    public static Integer GetId(ItemStack bag){
        return ItemHandler.getTagInt(bag, ItemHandler.FishBagId);
    }
}
